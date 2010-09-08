/**
 * PictureTaker.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.io.File;
import java.io.FileOutputStream;

import android.hardware.Camera;
import android.location.Location;
import android.util.Log;

/**
 * @author captainspam
 *
 */
public class PictureTaker {

    private static final String DEBUG_TAG = "PictureTaker";
    
    private String mPackageName;
    private String mDirName;
    private Annotator mAnnotator;

    public PictureTaker(String packageName) {
        mPackageName = packageName;
    }
    
    /**
     * Restarts this instance of PictureTaker.  That is to say, it starts a new
     * directory and readies any further SinglePictures for picturing.  Note
     * that past this, any old SinglePictures are invalid, and the behavior
     * from continuing to use them is undefined.
     * 
     * @return true if the directory was created, false if not
     */
    public boolean restart(Annotator annotator) {
        mAnnotator = annotator;
        // Create the directory.
        mDirName = "/sdcard/" + mPackageName + "/" + (System.currentTimeMillis() / 1000) + "/";
        boolean success = (new File(mDirName)).mkdirs();
        
        if(success)
            Log.d(DEBUG_TAG, "Directory " + mDirName + " created.");
        else
            Log.e(DEBUG_TAG, "Couldn't create " + mDirName + "!");
        
        return success;
    }
    
    /**
     * Gets a SinglePicture handle, prepped with the given Location and the
     * current directory name.
     * 
     * @param loc Location of choice
     * @return a SinglePicture, of course
     */
    public SinglePicture getPictureHandle(Location loc) {
        return new SinglePicture(loc, mDirName);
    }
    
    /**
     * A SinglePicture is one unit of picture-taking.  This stores the Location
     * of a single picture, writes the picture, and shoves it out to the
     * Annotator.
     * 
     * @author captainspam
     */
    public class SinglePicture implements Camera.PictureCallback {
        
        private Location mLocation;
        private String mDirName;
        
        /**
         * Constructs a SinglePicture with the given Location, ready for action.
         * 
         * @param loc Location at which this picture took place.
         */
        private SinglePicture(Location loc, String dirName) {
            mLocation = loc;
            mDirName = dirName;
        }

        @Override
        public void onPictureTaken(byte[] data, Camera camera) {
            String filename = mDirName + mLocation.getTime() + ".jpg";
            File output = new File(filename);
            try {
                // Write it out to SD as soon as possible, then pass it off to the
                // annotator.  We're working from disk in this case because this is
                // a phone.  We can't assume we can just keep grabbing RAM from
                // swap.  It's an embedded device, after all, and RAM can be very
                // limited.  The annotator just gets a string telling it where the
                // file is located.
                FileOutputStream ostream = new FileOutputStream(output);
                ostream.write(data);
                ostream.close();
                
                // Then, add to the annotator!
                mAnnotator.addPicture(new Annotator.IncomingPicture(filename, mLocation));
            } catch (Exception e) {
                Log.e(DEBUG_TAG, "EXCEPTION!");
                e.printStackTrace();
            }
            camera.startPreview();
        }
        
    }
}
