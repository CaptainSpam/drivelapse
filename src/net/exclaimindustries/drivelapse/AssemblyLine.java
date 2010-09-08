/**
 * AssemblyLine.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.io.Serializable;
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
public class AssemblyLine implements Runnable, Serializable {
    private static final long serialVersionUID = 1L;

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
    public static class WorkOrder implements Serializable {
        private static final long serialVersionUID = 1L;
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
    public final static class EndOrder extends WorkOrder {
        private static final long serialVersionUID = 1L;

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
     * 
     * @author Nicholas Killewald
     */
    public abstract static class Station implements Serializable {
        private static final long serialVersionUID = 1L;
        
        /** The AssemblyLine that owns this Station. */
        protected AssemblyLine mAl;
        
        public Station(AssemblyLine al) {
            mAl = al;
        }

        /**
         * Does any initialization needed for this Station.  This might include
         * starting up network connections, readying variables, etc, but also
         * must include resetting itself from a previous execution.
         */
        public abstract void init();

        /**
         * Processes a WorkOrder.  Note that this is synchronous; It'll return
         * when it gets done.
         *
         * @param order WorkOrder on which to work
         * @return true on success, false if something went wrong
         */
        public abstract boolean processOrder(WorkOrder order);

        /**
         * Finishes up whatever needs finishing up in this Station.  This could
         * be any amount of stateful things (finishing a movie, closing
         * connections, etc).
         *
         * This comes pre-defined to do nothing, since most Stations probably
         * won't do anything.  Override if need be.
         */
        public void finish() {}
        
        /**
         * Gets the name of this Station.  Each Station in a given AssemblyLine
         * needs a unique name.
         * 
         * @return this Station's name
         */
        public abstract String getName();
    }
    
    public AssemblyLine() {
        mStations = new LinkedList<Station>();
        mWorkOrders = new LinkedBlockingQueue<WorkOrder>();
        
        mThread = new Thread(this);
        mThread.setName("AssemblyLine Thread");
    }

    /**
     * Adds a WorkOrder to the line.  This will also wake up the thread if it
     * ran out of orders and is waiting on something.
     * 
     * @param order the new WorkOrder to add
     */
    public void addWorkOrder(WorkOrder order) {
        if(mThread == null || !mThread.isAlive()) {
            Log.w(DEBUG_TAG, "A new WorkOrder came in, but the thread isn't running!");
        } else {
            mWorkOrders.offer(order);
        }
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
            // Add it to the list!
            mStations.add(station);
        }
        else
            throw new IllegalThreadStateException("The AssemblyLine is currently running, so Stations can't be added!");
    }

    /**
     * The workers have a new contract, start up the AssemblyLine!
     */
    public void start() {
        for(Station s : mStations) {
            s.init();
        }
        mThread.start();
    }
    
    @Override
    public void run() {
        // And away we go!
        OrderType lastType = OrderType.PICTURE;
        
        do {
            WorkOrder order = null;
            try {
                // Block!
                order = mWorkOrders.take();
            } catch (InterruptedException e) {
                // INTERRUPTION!  Assume this means we stop.
                order = new EndOrder();
            }
            
            Log.d(DEBUG_TAG, "Order up!");
            lastType = order.getType();

            // Now, go through the stations one at a time and do whatever it is
            // that needs to be done.
            for(Station st : mStations) {
                st.processOrder(order);
            }
        } while(lastType != OrderType.END_QUEUE);
        
        Log.d(DEBUG_TAG, "DONE!");
        for(Station st ; mStations) {
            st.finish();
        }
    }
    
    /**
     * Determines if this AssemblyLine is currently running.
     * 
     * @return true if it's running, false if it's not
     */
    public boolean isAlive() {
        return (mThread != null && mThread.isAlive());
    }
}
