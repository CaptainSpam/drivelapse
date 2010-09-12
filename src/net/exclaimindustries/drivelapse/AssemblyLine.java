/**
 * AssemblyLine.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.io.File;
import java.io.FileOutputStream;
import java.util.LinkedList;

import android.app.IntentService;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.location.Location;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

/**
 * The AssemblyLine is where a series of picture go to get themselves all
 * processed up.  It has an ordered series of Stations that process
 * WorkOrders given to it by an OrderProducer.
 * 
 * @author Nicholas Killewald
 */
public class AssemblyLine extends IntentService {

    private static final String DEBUG_TAG = "AssemblyLine";
    
    public static final String WORK_ORDER = "net.exclaimindustries.drivelapse.workorder";
    
    /**
     * A WorkOrder is the file and GPS location of a single picture to be worked
     * on.  Presumably, the file won't go away as we go along.  A WorkOrder can
     * also contain a special command, such as one to indicate the end of the
     * queue entirely.
     * 
     * @author Nicholas Killewald
     */
    public static class WorkOrder implements Parcelable {
        protected String mFileLocation;
        protected Location mGpsLocation;
        protected Canvas mWorkingCanvas;
        protected Bundle mExtraData;
        
        public static final Parcelable.Creator<WorkOrder> CREATOR = new Parcelable.Creator<WorkOrder>() {
            public WorkOrder createFromParcel(Parcel in) {
                return new WorkOrder(in);
            }

            public WorkOrder[] newArray(int size) {
                return new WorkOrder[size];
            }
        };
        
        public WorkOrder(String fileLocation, Location gpsLocation) {
            mFileLocation = fileLocation;
            mGpsLocation = gpsLocation;
            mExtraData = new Bundle();
        }
        
        public WorkOrder(Parcel in) {
            readFromParcel(in);
        }
        
        /**
         * Gets the location of the image file this WorkOrder is working on.
         * This is also where the processed image will be written.
         * 
         * @return the image's location
         */
        public String getFileLocation() {
            return mFileLocation;
        }

        /**
         * Gets the GPS location of the user when the image attached to this
         * WorkOrder was taken.  This isn't part of the extra data Bundle (yet)
         * because this is sort of vital to what DriveLapse is doing in the
         * first place.
         * 
         * @return the GPS location of the user
         */
        public Location getGpsLocation() {
            return mGpsLocation;
        }
        
        /**
         * Sets the canvas for future Stations to work on.  Note that, to save
         * memory, this should ONLY be set right before the Stations kick in.
         * Also note that the Canvas isn't parcelable.
         * 
         * @param canvas Canvas with the appropriate bitmap data on it
         */
        private void setCanvas(Canvas canvas) {
            mWorkingCanvas = canvas; 
        }
        
        /**
         * Gets the current canvas this WorkOrder has.  This can be null if the
         * AssemblyLine didn't create it yet.  Which shouldn't happen.
         * 
         * @return the current Canvas
         */
        public Canvas getCanvas() {
            return mWorkingCanvas;
        }
        
        /**
         * Gets a Bundle of extra data.  This can include whatever nonsense you
         * can think of (that can fit in a Bundle), and can be useful for
         * passing data between Stations.
         * 
         * @return the current Bundle of data in this WorkOrder
         */
        public Bundle getExtraData() {
            return mExtraData;
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // WRITE!
            dest.writeString(mFileLocation);
            dest.writeParcelable(mGpsLocation, flags);
            dest.writeBundle(mExtraData);
        }
        
        /**
         * Reads an incoming Parcel and deparcelizes it.  I like the word
         * "deparcelize" so much, I'm importing it from Geohash Droid.
         * 
         * @param in parcel to deparcelize
         */
        public void readFromParcel(Parcel in) {
            // Go!
            mFileLocation = in.readString();
            mGpsLocation = (Location)(in.readParcelable(null));
            mExtraData = in.readBundle();
        }
    }
    
    /**
     * A Station is one discrete modification instruction to be performed on a
     * WorkOrder.  Such instructions can be, for instance, annotation, exif
     * handling, movie construction, etc, etc.  Note that it is expected that
     * the image will stay on disk in the same spot as it was to begin with.
     * 
     * @author Nicholas Killewald
     */
    public abstract static class Station {
        /**
         * Processes a WorkOrder.  Note that this is synchronous; It'll return
         * when it gets done.
         *
         * @param order WorkOrder on which to work
         * @return true on success, false if something went wrong
         */
        public abstract boolean processOrder(WorkOrder order);
        
        /**
         * Gets the name of this Station.  Each Station in a given AssemblyLine
         * needs a unique name.
         * 
         * @return this Station's name
         */
        public abstract String getName();
    }
    
    public AssemblyLine() {
        super("AssemblyLine");
    }
     
    @Override
    protected void onHandleIntent(Intent intent) {
        // First off, grab the WorkOrder.
        Log.d(DEBUG_TAG, "Order up!");
        WorkOrder order = (WorkOrder)(intent.getParcelableExtra(WORK_ORDER));
        
        // Make all our stations.  This is a very temporary setup; I'm certain
        // there HAS to be a better way to do this (in fact, the whole
        // IntentService thing may be changed significantly later).
        LinkedList<Station> stations = new LinkedList<Station>();
        
        stations.add(new Annotator(this));
        
        // Now, let's crack that image open and get some tasty, tasty data.
        try {
            Bitmap origBitmap = BitmapFactory.decodeFile(order.getFileLocation());
            Bitmap bitmap = origBitmap.copy(origBitmap.getConfig(), true);
            origBitmap.recycle();
            origBitmap = null;
            order.setCanvas(new Canvas(bitmap));
            
            // Fire up the stations!
            for(Station st : stations) {
                st.processOrder(order);
            }
            
            // With all the stations done working on the canvas, write it back
            // out to SD.  Or, y'know, wherever it leads.
            File output = new File(order.getFileLocation());
            
            FileOutputStream ostream = null;
            ostream = new FileOutputStream(output);
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, ostream);
            ostream.close();
            
            bitmap.recycle();
            bitmap = null;
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }

        
        Log.d(DEBUG_TAG, "Order finished!");
    }
}
