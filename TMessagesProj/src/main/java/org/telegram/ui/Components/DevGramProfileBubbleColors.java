package org.telegram.ui.Components;
import org.json.JSONObject;
/** RGB fields remain compatible; their original 8-bit alpha is stored independently. */
public final class DevGramProfileBubbleColors {
    private DevGramProfileBubbleColors(){}
    public static String alphaKey(String rgb){return rgb.equals("bubbleColor2")?"bubbleColor2Alpha":rgb.equals("textColor")?"bubbleTextAlpha":"bubbleAlpha";}
    public static int color(JSONObject doc,String rgb,int fallback){int alpha=Math.max(0,Math.min(255,doc.optInt(alphaKey(rgb),fallback>>>24)));return (alpha<<24)|(doc.optInt(rgb,fallback)&0xffffff);}
}
