package com.google.sites.radikaiwarehouse.flashairbrowsepie;

import android.util.Log;

public class FlashAirFileEntry {
    private String m_dirname;
    private String m_filename;
    private int m_filesize;
    private int m_attribute;
    private int m_date;
    private int m_time;

    private static final int MASK_ARCHIVE = 0x20;
    private static final int MASK_DIRECTORY = 0x10;
    private static final int MASK_VOLUME = 0x08;
    private static final int MASK_SYSTEM = 0x04;
    private static final int MASK_HIDDEN = 0x02;
    private static final int MASK_READONLY = 0x01;

    public FlashAirFileEntry(String dirname, String filename, int filesize, int attribute, int date, int time) {
        m_dirname = dirname;
        m_filename = filename;
        m_filesize = filesize;
        m_attribute = attribute;
        m_date = date;
        m_time = time;
    }

    public static FlashAirFileEntry fromString(String str) {
        String items[] = str.split(",");
        if(items.length!=6) {
            return null;
        }
        try {
            return new FlashAirFileEntry(
                items[0], items[1],
                Integer.parseInt(items[2]), Integer.parseInt(items[3]),
                Integer.parseInt(items[4]), Integer.parseInt(items[5])
            );
        }catch(Exception e) {
            Log.d("FlashAirFileEntry", e.toString());
            return null;
        }
    }

    public String getParentDir() {
        return m_dirname;
    }

    public String getFilename() {
        return m_filename;
    }

    public String getFullPathName() {
        return m_dirname + "/" + m_filename;
    }

    public boolean isDir() {
        return (m_attribute & MASK_DIRECTORY) != 0x00;
    }
}
