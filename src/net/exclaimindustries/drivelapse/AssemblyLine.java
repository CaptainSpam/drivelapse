/**
 * AssemblyLine.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.util.LinkedList;

import android.app.IntentService;
import android.content.Intent;
import android.location.Location;
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
            
        }
        
        public WorkOrder(Parcel in) {
            readFromParcel(in);
        }
        
        public String getFileLocation() {
            return mFileLocation;
        }

        public Location getGpsLocation() {
            return mGpsLocation;
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
        
        for(Station st : stations) {
            st.processOrder(order);
        }
        Log.d(DEBUG_TAG, "Order finished!");
    }
}
