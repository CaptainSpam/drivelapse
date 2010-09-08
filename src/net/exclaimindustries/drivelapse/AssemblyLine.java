/**
 * AssemblyLine.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;

import android.location.Location;
import android.util.Log;

/**
 * The AssemblyLine is where a series of picture go to get themselves all
 * processed up.  It has an ordered series of Stations that process
 * WorkOrders given to it by an OrderProducer.
 * 
 * @author Nicholas Killewald
 */
public class AssemblyLine implements Runnable {
    /** Enum dictating what type of WorkOrder we're dealing with. */
    public static enum OrderType {
        /** A plain picture. */
        PICTURE,
        /** An order to end the queue at this point. */
        END_QUEUE
    };
    
    private static final String DEBUG_TAG = "AssemblyLine";
    
    // The stations, in order.
    private LinkedList<Station> mStations;
    // The queue of orders.
    private LinkedBlockingQueue<WorkOrder> mWorkOrders;
    // An AssemblyLine is a running thread. 
    private Thread mThread;
    
    /**
     * A WorkOrder is the file and GPS location of a single picture to be worked
     * on.  Presumably, the file won't go away as we go along.  A WorkOrder can
     * also contain a special command, such as one to indicate the end of the
     * queue entirely.
     * 
     * @author Nicholas Killewald
     */
    public class WorkOrder {
        protected String mFileLocation;
        protected Location mGpsLocation;
        protected Map<String, String> mExifTags;
        
        public WorkOrder(String fileLocation, Location gpsLocation) {
            mFileLocation = fileLocation;
            mGpsLocation = gpsLocation;
            
            mExifTags = new HashMap<String,String>();
        }
        
        public String getFileLocation() {
            return mFileLocation;
        }

        public Location getGpsLocation() {
            return mGpsLocation;
        }
        
        public Map<String, String> getExifTags() {
            return mExifTags;
        }

        /**
         * Returns the type of order this is.
         * 
         * @return OrderType.PICTURE if it's a picture, ObjectType.STOP_COMMAND
         *         if this is the end of the queue and the Annotator thread
         *         should stop now
         */
        public OrderType getType() {
            return OrderType.PICTURE;
        }    
    }
    
    /**
     * An EndOrder is an order whose sole purpose is to tell the AssemblyLine
     * that it's the end of the queue at this point, and it should shut down
     * once the last order's done.
     * 
     * @author Nicholas Killewald
     */
    public final class EndOrder extends WorkOrder {
        public EndOrder() {
            super(null, null);
        }
        
        public OrderType getType() {
            return OrderType.END_QUEUE;
        }
    }
    
    /**
     * A Station is one discrete modification instruction to be performed on a
     * WorkOrder.  Such instructions can be, for instance, annotation, exif
     * handling, movie construction, etc, etc.  Note that it is expected that
     * the image will stay on disk in the same spot as it was to begin with.
     * Also note that Stations are Runnables. 
     * 
     * @author Nicholas Killewald
     */
    public abstract class Station implements Runnable {
        /** The orders this station has yet to run. */
        protected LinkedBlockingQueue<WorkOrder> mOrderQueue;
        
        /** The current thread this Runnable is running under. */
        protected Thread mThread;
        
        /** The AssemblyLine that owns this Station. */
        protected AssemblyLine mAl;
        
        /** The next station in the line.  If null, this is the end. */
        private Station mNextStation;
        
        public Station(AssemblyLine al) {
            mAl = al;
            mOrderQueue = new LinkedBlockingQueue<WorkOrder>();
            mThread = new Thread(this);
            mThread.setName(getName() + " Station");
        }
        
        /**
         * Adds an order to the Station.  It'll get done when it gets done.
         * 
         * @param order WorkOrder to add
         * @return true if the order got added, false if it didn't (either
         *         meaning the queue is full, this Station hasn't started yet,
         *         this Station finished up, or the AssemblyLine is on strike)
         */
        public boolean addOrder(WorkOrder order) {
            if(mThread != null && mThread.isAlive())
                return mOrderQueue.offer(order);
            else
            {
                Log.w(getName(), "A WorkOrder came in from the AssemblyLine, but this Station's thread isn't alive!");
                return false;
            }
        }
        
        /**
         * Gets the name of this Station.  Each Station in a given AssemblyLine
         * needs a unique name.  This is also used as the thread name, with a
         * space and the word "Station" after it.  So don't put "Station" in the
         * name.  It'll look silly.
         * 
         * @return this Station's name
         */
        public abstract String getName();
        
        private void setNextStation(Station next) {
            mNextStation = next;
        }
        
        private Station getNextStation() {
            return mNextStation;
        }
        
        protected void finishOrder(WorkOrder order) {
            mAl.stationDone(order, this);
        }
    }

    /**
     * Adds a WorkOrder to the line.  This will also wake up the thread if it
     * ran out of orders and is waiting on something.
     * 
     * @param order the new WorkOrder to add
     */
    public void addWorkOrder(WorkOrder order) {
        if(mThread != null && mThread.isAlive())
            mWorkOrders.offer(order);
        else
            Log.e(DEBUG_TAG, "A WorkOrder came in, but the AssemblyLine isn't open!");
    }
    
    /**
     * Adds an EndOrder to the line.  That is, it indicates the end of the line
     * and will stop all the Station threads and this thread itself, once all
     * other orders are finished.
     */
    public void addEndOrder() {
        addWorkOrder(new EndOrder());
    }
    
    /**
     * Adds a Station to the end of the line.  Remember to add them in order!
     * And you can't add them if the line is active.
     * 
     * @param station Station to add
     */
    public void addStation(Station station) {
        if(mThread == null || !mThread.isAlive()) {
            // If the list isn't empty, let the current last one know what its
            // new next one is.
            if(!mStations.isEmpty()) {
                mStations.getLast().setNextStation(station);
            }
            mStations.add(station);
        }
        else
            throw new IllegalThreadStateException("The AssemblyLine is currently running, so Stations can't be added!");
    }

    @Override
    public void run() {
        // And away we go!
        
    }
    
    /**
     * Called by Stations when they finish a WorkOrder.  The AssemblyLine will
     * then feed it to the next Station, if there is one.  Don't you just love
     * baffling inner class access?
     * 
     * @param order the WorkOrder finished
     * @param last the Station that just finished it
     */
    private void stationDone(WorkOrder order, Station last) {
        if(last.getNextStation() != null) {
            // If there's another station, queue it up there.
            last.getNextStation().addOrder(order);
        } else {
            // Otherwise, we're done!
        }
            
    }
}