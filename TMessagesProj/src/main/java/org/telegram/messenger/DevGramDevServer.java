package org.telegram.messenger;

import fi.iki.elonen.NanoHTTPD;
import java.io.File;
import java.util.Map;

/** Local-only development endpoint for installing a .dgplugin over ADB. */
public final class DevGramDevServer extends NanoHTTPD {
    private static DevGramDevServer instance;
    private final String token;

    private DevGramDevServer(int port, String token) { super("127.0.0.1", port); this.token = token; }

    public static synchronized boolean start(int port, String token) {
        try {
            stopServer();
            instance = new DevGramDevServer(port, token == null ? "" : token);
            instance.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
            return true;
        } catch (Throwable e) { FileLog.e(e); instance = null; return false; }
    }

    public static synchronized void stopServer() {
        if (instance != null) { try { instance.stop(); } catch (Throwable ignore) {} instance = null; }
    }

    public static synchronized boolean isRunning() { return instance != null; }

    @Override public Response serve(IHTTPSession session) {
        if (!"127.0.0.1".equals(session.getRemoteIpAddress()) && !"localhost".equals(session.getRemoteIpAddress()))
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "local only");
        String supplied = session.getHeaders().get("x-devgram-token");
        if (token.length() > 0 && (supplied == null || !token.equals(supplied)))
            return newFixedLengthResponse(Response.Status.UNAUTHORIZED, MIME_PLAINTEXT, "bad token");
        if ((Method.POST.equals(session.getMethod()) || Method.PUT.equals(session.getMethod()))
                && "/upload".equals(session.getUri())) {
            File upload = null;
            try {
                Map<String, String> files = new java.util.HashMap<>();
                session.parseBody(files);
                // NanoHTTPD stores multipart files under their field name. For a raw
                // PUT it uses "content". Raw binary POST must not be read from
                // "postData": NanoHTTPD decodes that value as text and corrupts ZIPs.
                String contentType = session.getHeaders().get("content-type");
                boolean multipart = contentType != null
                        && contentType.toLowerCase(java.util.Locale.US).startsWith("multipart/form-data");
                String sourcePath = files.get("file");
                // Older DevGram builds looked specifically for this field name.
                // It is safe here only for multipart, where the value is a temp path.
                if (sourcePath == null && multipart) sourcePath = files.get("postData");
                if (sourcePath == null) sourcePath = files.get("content");
                if (sourcePath == null) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                            "upload must be multipart/form-data (field: postData) or raw PUT");
                }
                File source = new File(sourcePath);
                if (!source.isFile() || source.length() == 0) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT, "empty upload");
                }
                upload = File.createTempFile("devgram-upload-", ".dgplugin",
                        ApplicationLoader.applicationContext.getCacheDir());
                java.nio.file.Files.copy(source.toPath(), upload.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                String validationError = DevGramPlugins.packageValidationError(upload.getAbsolutePath());
                if (validationError != null && !validationError.isEmpty()) {
                    return newFixedLengthResponse(Response.Status.BAD_REQUEST, MIME_PLAINTEXT,
                            "invalid package: " + validationError);
                }
                boolean ok = DevGramPlugins.installPackage(upload.getAbsolutePath(), null, true);
                return newFixedLengthResponse(ok ? Response.Status.OK : Response.Status.BAD_REQUEST,
                        MIME_PLAINTEXT, ok ? "installed=true" : "installed=false; plugin failed to load");
            } catch (Throwable e) {
                FileLog.e(e);
                String message = e.getMessage();
                if (message == null || message.trim().isEmpty()) message = "unknown error";
                return newFixedLengthResponse(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT,
                        "upload failed: " + e.getClass().getSimpleName() + ": " + message);
            } finally {
                if (upload != null && upload.exists() && !upload.delete()) {
                    FileLog.e("DevGramDevServer: failed to delete temporary upload");
                }
            }
        }
        if (Method.POST.equals(session.getMethod()) && "/reload".equals(session.getUri())) {
            String pluginId = session.getParms().get("plugin");
            int count = pluginId == null || pluginId.isEmpty() ? DevGramPlugins.reload() : DevGramPlugins.reloadPlugin(pluginId);
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT, "reloaded=" + count);
        }
        if (Method.POST.equals(session.getMethod()) && "/debugger/start".equals(session.getUri())) {
            String platform = session.getParms().get("platform");
            String host = session.getParms().get("host");
            String portText = session.getParms().get("port");
            int port = 5678;
            try { if (portText != null) port = Integer.parseInt(portText); } catch (Throwable ignore) { }
            if (platform == null || platform.isEmpty()) platform = "vscode";
            if (host == null || host.isEmpty()) host = "127.0.0.1";
            String result = DevGramPlugins.startRemoteDebugger(platform, host, port);
            return newFixedLengthResponse(result.startsWith("error") ? Response.Status.INTERNAL_ERROR : Response.Status.OK,
                    MIME_PLAINTEXT, "debugger=" + result);
        }
        if (Method.POST.equals(session.getMethod()) && "/debugger/stop".equals(session.getUri())) {
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "debugger=" + DevGramPlugins.stopRemoteDebugger());
        }
        if (Method.GET.equals(session.getMethod()) && "/status".equals(session.getUri()))
            return newFixedLengthResponse(Response.Status.OK, MIME_PLAINTEXT,
                    "ok;debugger=" + DevGramPlugins.remoteDebuggerStatus());
        return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "not found");
    }
}
