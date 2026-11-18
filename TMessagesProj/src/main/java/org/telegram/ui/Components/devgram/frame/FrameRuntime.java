package org.telegram.ui.Components.devgram.frame;
final class FrameRuntime {
 static final String AVATAR="avatar:own";
 static boolean isAvatar(String source){return AVATAR.equals(source);}
 static boolean isBlank(String source){return source!=null&&source.matches("blank:(rim|ring|stripe|bead|dot|gem|star|spark)");}
 static boolean isAnimated(String source){return source!=null&&source.matches("(?i).*\\.(gif|apng|webp)$");}
}
