/*
 * DevGram: массовая очистка аккаунта — «Другое» → «Очистить аккаунт». Контакты, личные чаты
 * (в т.ч. секретные — «для себя», без revoke), выход из групп/каналов, черновики.
 *
 * Диалоги обрабатываются НЕ все разом одним залпом запросов, а по одному с небольшой паузой —
 * иначе сотни одновременных запросов на удаление/выход рискуют словить flood-control сервера на
 * реальном аккаунте. Личные чаты чистятся только «для себя» (revoke=false) — переписка у
 * собеседника не трогается; группы/каналы — просто выходим, ничего не удаляем у остальных
 * участников (тот же метод, что и у обычного «выйти из чата» в меню одного диалога).
 */

package org.telegram.messenger;

import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

public class DevGramAccountCleanup {

    private static final int STEP_DELAY_MS = 180;

    public static class Options {
        public boolean contacts;
        public boolean chats;
        public boolean groups;
        public boolean drafts;
        public boolean savedMessages;

        public boolean any() {
            return contacts || chats || groups || drafts || savedMessages;
        }
    }

    public interface Callback {
        // stage: человекочитаемое описание текущего шага, done/total — прогресс внутри шага (0/0 если не применимо)
        void onStage(String stage, int done, int total);
        void onDone();
    }

    public static class Target {
        public int contactsCount;
        public int chatsCount;
        public int groupsCount;
        public boolean savedMessagesPresent;

        public int total() {
            return contactsCount + chatsCount + groupsCount + (savedMessagesPresent ? 1 : 0);
        }
    }

    // Посчитать, сколько чего будет затронуто — для текста подтверждения (сколько контактов/чатов
    // реально удалится по выбранным галочкам).
    public static Target count(int account, Options o) {
        Target t = new Target();
        if (o.contacts) {
            t.contactsCount = ContactsController.getInstance(account).contacts.size();
        }
        long selfId = UserConfig.getInstance(account).getClientUserId();
        MessagesController mc = MessagesController.getInstance(account);
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            long did = d.id;
            if (did == 0 || DialogObject.isFolderDialogId(did)) {
                continue;
            }
            if (did == selfId) {
                if (o.savedMessages) {
                    t.savedMessagesPresent = true;
                }
                continue;
            }
            if (DialogObject.isChatDialog(did)) {
                if (o.groups) {
                    t.groupsCount++;
                }
            } else {
                // пользователь или секретный чат — считаем «личным чатом»
                if (o.chats) {
                    t.chatsCount++;
                }
            }
        }
        return t;
    }

    public static void run(int account, Options o, Callback cb) {
        MessagesController mc = MessagesController.getInstance(account);
        long selfId = UserConfig.getInstance(account).getClientUserId();

        ArrayList<Long> chatTargets = new ArrayList<>();
        ArrayList<Long> groupTargets = new ArrayList<>();
        boolean wipeSaved = false;
        for (TLRPC.Dialog d : new ArrayList<>(mc.getAllDialogs())) {
            long did = d.id;
            if (did == 0 || DialogObject.isFolderDialogId(did)) {
                continue;
            }
            if (did == selfId) {
                if (o.savedMessages) {
                    wipeSaved = true;
                }
                continue;
            }
            if (DialogObject.isChatDialog(did)) {
                if (o.groups) {
                    groupTargets.add(did);
                }
            } else if (o.chats) {
                chatTargets.add(did);
            }
        }
        final boolean wipeSavedF = wipeSaved;

        Runnable[] steps = new Runnable[5];
        int[] idx = {0};

        steps[0] = () -> {
            if (o.contacts && !ContactsController.getInstance(account).contacts.isEmpty()) {
                int n = ContactsController.getInstance(account).contacts.size();
                cb.onStage(DevGramLocale.text("Удаляем контакты…"), 0, n);
                ContactsController.getInstance(account).deleteAllContacts(() -> {
                    cb.onStage(DevGramLocale.text("Контакты удалены"), n, n);
                    AndroidUtilities.runOnUIThread(() -> next(steps, idx));
                });
            } else {
                next(steps, idx);
            }
        };

        steps[1] = () -> processDialogs(account, chatTargets, DevGramLocale.text("Очищаем личные чаты…"), steps, idx, cb);
        steps[2] = () -> processDialogs(account, groupTargets, DevGramLocale.text("Выходим из групп и каналов…"), steps, idx, cb);

        steps[3] = () -> {
            if (wipeSavedF) {
                cb.onStage(DevGramLocale.text("Очищаем «Сохранённое»…"), 0, 1);
                mc.deleteDialog(selfId, 0, false);
            }
            if (o.drafts) {
                cb.onStage(DevGramLocale.text("Чистим черновики…"), 0, 1);
                mc.getMediaDataController().clearAllDrafts(true);
            }
            next(steps, idx);
        };

        steps[4] = cb::onDone;

        next(steps, idx);
    }

    private static void next(Runnable[] steps, int[] idx) {
        if (idx[0] >= steps.length) {
            return;
        }
        Runnable step = steps[idx[0]++];
        step.run();
    }

    // Идём по списку диалогов по одному, с паузой между запросами (см. комментарий в шапке файла).
    private static void processDialogs(int account, ArrayList<Long> ids, String stage, Runnable[] steps, int[] idx, Callback cb) {
        if (ids.isEmpty()) {
            next(steps, idx);
            return;
        }
        int total = ids.size();
        int[] done = {0};
        processOne(account, ids, 0, total, done, stage, cb, () -> next(steps, idx));
    }

    private static void processOne(int account, ArrayList<Long> ids, int pos, int total, int[] done, String stage, Callback cb, Runnable onFinished) {
        if (pos >= ids.size()) {
            onFinished.run();
            return;
        }
        long did = ids.get(pos);
        cb.onStage(stage, done[0], total);
        MessagesController mc = MessagesController.getInstance(account);
        if (DialogObject.isChatDialog(did)) {
            TLRPC.Chat chat = mc.getChat(-did);
            if (chat == null || ChatObject.isNotInChat(chat)) {
                mc.deleteDialog(did, 0, false);
            } else {
                // forceDelete=true: если пользователь — создатель канала/супергруппы, чат удаляется
                // ЦЕЛИКОМ для всех участников; иначе (или для обычной группы) — просто выход, метод
                // сам разбирает это внутри (см. MessagesController.deleteParticipantFromChat).
                TLRPC.User self = mc.getUser(UserConfig.getInstance(account).getClientUserId());
                mc.deleteParticipantFromChat(-did, self, chat, true, true);
            }
        } else {
            // личный/секретный чат: revoke=true — переписка удаляется и у собеседника тоже.
            mc.deleteDialog(did, 0, true);
        }
        done[0]++;
        AndroidUtilities.runOnUIThread(() -> processOne(account, ids, pos + 1, total, done, stage, cb, onFinished), STEP_DELAY_MS);
    }
}
