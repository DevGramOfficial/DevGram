package org.telegram.messenger;

import org.json.JSONObject;

/** Per-profile memoization. Draft writes invalidate explicitly; policy/role events invalidate by revision. */
public final class DevGramProfileRenderCache {
    private JSONObject draftSource, draftValue, headerSource, headerValue;
    private long draftUid;
    private int draftVersion;
    private long headerSignature;

    public void invalidateDraft() { draftSource = null; }
    public JSONObject draft(JSONObject source, long uid) {
        int version = DevGramBadges.styleVersion;
        if (source != draftSource || uid != draftUid || version != draftVersion) {
            draftValue = DevGramProfileDocument.effective(source, uid);
            draftSource = source; draftUid = uid; draftVersion = version;
        }
        return draftValue;
    }
    public JSONObject header(JSONObject document) {
        if (document != headerSource) {
            headerValue = DevGramHeaderLayout.config(document);
            headerSignature = headerValue.toString().hashCode();
            headerSignature = headerSignature * 31 + document.optInt("nameSize", 100);
            headerSignature = headerSignature * 31 + document.optInt("nameFont", document.optInt("font"));
            JSONObject font = document.optJSONObject("nameFontFile");
            headerSignature = headerSignature * 31 + (font == null ? 0 : font.toString().hashCode());
            headerSource = document;
        }
        return headerValue;
    }
    public long headerSignature() { return headerSignature; }
}
