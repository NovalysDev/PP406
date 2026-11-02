package com.cockpit406.diagnostic;
import android.content.*;
import android.database.*;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.*;

/** Only the explicitly granted, verified update APK is readable. No directory or write access. */
public final class UpdateApkProvider extends ContentProvider {
    public boolean onCreate(){return true;}
    private void check(Uri uri){if(!AppUpdates.AUTHORITY.equals(uri.getAuthority())||!"/apk".equals(uri.getPath())||uri.getQuery()!=null)throw new IllegalArgumentException("Adresse de mise à jour inconnue");}
    public String getType(Uri uri){check(uri);return "application/vnd.android.package-archive";}
    public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{check(uri);if(!"r".equals(mode))throw new SecurityException("Lecture seule");return ParcelFileDescriptor.open(AppUpdates.apkFile(getContext()),ParcelFileDescriptor.MODE_READ_ONLY);}
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){check(uri);String[] columns=projection==null?new String[]{OpenableColumns.DISPLAY_NAME,OpenableColumns.SIZE}:projection;MatrixCursor cursor=new MatrixCursor(columns);Object[] row=new Object[columns.length];for(int i=0;i<columns.length;i++){if(OpenableColumns.DISPLAY_NAME.equals(columns[i]))row[i]="PP406-update.apk";else if(OpenableColumns.SIZE.equals(columns[i]))row[i]=AppUpdates.apkFile(getContext()).length();}cursor.addRow(row);return cursor;}
    public Uri insert(Uri uri,ContentValues values){throw new SecurityException("Lecture seule");}
    public int delete(Uri uri,String selection,String[] args){throw new SecurityException("Lecture seule");}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new SecurityException("Lecture seule");}
}
