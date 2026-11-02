package com.cockpit406.diagnostic;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.function.Consumer;

/** Best-effort private journal. Storage failures must never interrupt vehicle I/O or cleanup. */
public final class SessionJournal extends PrintWriter {
    private static final String UNAVAILABLE="Journal indisponible · le diagnostic reste utilisable, sans enregistrement";
    private final Sink sink;

    private SessionJournal(Sink sink) { super(sink); this.sink=sink; }

    // Package access allows failure injection without involving Android or a vehicle.
    SessionJournal(Writer target,Consumer<String> notice) { this(new Sink(target,null,false,notice)); }

    public static SessionJournal open(File filesRoot,File cacheRoot,String prefix,Consumer<String> notice) {
        SimpleDateFormat stamp=new SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'",Locale.ROOT);
        stamp.setTimeZone(TimeZone.getTimeZone("UTC"));
        String name=prefix+"-"+stamp.format(new Date())+"-";
        File[] roots={filesRoot,cacheRoot};
        for(int i=0;i<roots.length;i++) {
            if(roots[i]==null)continue;
            try {
                File directory=new File(roots[i],"sessions");
                if(!directory.isDirectory()&&!directory.mkdirs())continue;
                File file=File.createTempFile(name,".jsonl",directory);
                Writer target=new OutputStreamWriter(new FileOutputStream(file),StandardCharsets.UTF_8);
                return new SessionJournal(new Sink(target,file,i==1,notice));
            } catch(IOException|SecurityException ignored) { /* Try the other private directory. */ }
        }
        Sink sink=new Sink(null,null,false,notice);
        sink.warn();
        return new SessionJournal(sink);
    }

    public String description() {
        if(!sink.available)return UNAVAILABLE;
        return (sink.temporary?"Journal temporaire : ":"Journal : ")+(sink.file==null?"en cours":sink.file.getName());
    }
    public File file() { return sink.file; }
    public boolean available() { return sink.available; }

    private static final class Sink extends Writer {
        private Writer target;
        private final File file;
        private final boolean temporary;
        private final Consumer<String> notice;
        private volatile boolean available;
        private boolean warned;

        Sink(Writer target,File file,boolean temporary,Consumer<String> notice) {
            this.target=target;this.file=file;this.temporary=temporary;this.notice=notice;available=target!=null;
        }
        private void warn() {
            if(warned)return;
            warned=true;
            // A destroyed UI or a failed notice must not affect the diagnostic session either.
            try { if(notice!=null)notice.accept(UNAVAILABLE); } catch(RuntimeException ignored) {}
        }
        private void failed() {
            available=false;
            Writer previous=target;target=null;
            try { if(previous!=null)previous.close(); } catch(IOException|SecurityException ignored) {}
            warn();
        }
        @Override public void write(char[] buffer,int offset,int length) {
            if(target==null)return;
            try { target.write(buffer,offset,length); } catch(IOException|SecurityException ignored) { failed(); }
        }
        @Override public void flush() {
            if(target==null)return;
            try { target.flush(); } catch(IOException|SecurityException ignored) { failed(); }
        }
        @Override public void close() {
            Writer previous=target;target=null;
            try { if(previous!=null)previous.close(); } catch(IOException|SecurityException ignored) { available=false;warn(); }
        }
    }
}
