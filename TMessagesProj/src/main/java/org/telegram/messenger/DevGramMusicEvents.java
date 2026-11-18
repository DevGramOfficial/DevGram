package org.telegram.messenger;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** A visible profile row subscribes only to its owner's playback revision, never credentials in URLs. */
public final class DevGramMusicEvents implements AutoCloseable {
    private volatile boolean closed;
    private volatile HttpURLConnection connection;
    private final Thread thread;
    public DevGramMusicEvents(int account,long owner,String block,Runnable changed){
        long viewer=UserConfig.getInstance(account).getClientUserId();
        String token=DevGramCommunityProfiles.owner(owner)==viewer?DevGramConfig.getBetaToken(viewer):"";
        thread=new Thread(()->{
            long delay=1000;
            while(!closed&&DevGramProfileCustomization.enabled()&&UserConfig.getInstance(account).getClientUserId()==viewer){
                HttpURLConnection request=null;
                try{
                    request=(HttpURLConnection)new URL(DevGramProfilePolicy.BASE+"studio/activity/events/"+owner+"?block="+URLEncoder.encode(block,"UTF-8")).openConnection();
                    connection=request;if(closed)break;
                    request.setConnectTimeout(12000);request.setReadTimeout(45000);request.setRequestProperty("Accept","text/event-stream");
                    if(!token.isEmpty())request.setRequestProperty("X-DevGram-Token",token);
                    if(request.getResponseCode()!=200)throw new java.io.IOException();
                    delay=1000;
                    try(BufferedReader reader=new BufferedReader(new InputStreamReader(request.getInputStream(),StandardCharsets.UTF_8))){
                        String line;
                        while(!closed&&(line=reader.readLine())!=null){
                            if(line.startsWith("data: ")&&line.length()<256)AndroidUtilities.runOnUIThread(()->{if(!closed&&UserConfig.getInstance(account).getClientUserId()==viewer&&DevGramProfileCustomization.enabled())changed.run();});
                        }
                    }
                }catch(Exception ignored){/* Reconnect quietly; no tokens or transport URLs in logs. */}
                finally{if(request!=null)request.disconnect();if(connection==request)connection=null;}
                if(closed)break;
                try{Thread.sleep(delay);}catch(InterruptedException ignored){break;}
                delay=Math.min(30000,delay*2);
            }
        },"DevGram-music-events");thread.setDaemon(true);thread.start();
    }
    @Override public void close(){closed=true;thread.interrupt();HttpURLConnection current=connection;if(current!=null)current.disconnect();}
}
