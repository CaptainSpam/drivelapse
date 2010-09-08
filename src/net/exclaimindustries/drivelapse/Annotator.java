/**
 * Annotator.java
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.DateFormat;
import java.util.Calendar;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.location.Address;
import android.location.Geocoder;
import android.util.Log;

/**
 * The Annotator does annotation.  Obviously.  It kicks off a thread that does
 * all modifications to the image as need be, mostly involving putting the
 * various info boxes on it.
 * 
 * @author captainspam
 */
public class Annotator extends AssemblyLine.Station {
    private static final String DEBUG_TAG = "Annotator";
    
    private Paint mBackgroundPaint;
    private Paint mTextPaint;
    
    private Geocoder mGeocoder;
    private Context mContext;
    
    // The following three are defined once the first picture comes in.
    /** How tall a box is. */
    private int mBoxHeight;
    /** Amount of padding in the box itself. */
    private int mBoxPadding;
    /** Distance between the box and the side and bottom of the pic. */
    private int mBoxMargin;
   
    public Annotator(AssemblyLine al, Context context) {
        // Ready to annotate!
        super(al);
        mGeocoder = new Geocoder(context);
        mContext = context;
        
        mBackgroundPaint = new Paint();
        mBackgroundPaint.setStyle(Style.FILL);
        mBackgroundPaint.setColor(mContext.getResources().getColor(R.color.annotation_background));
        
        // The text paint gets defined first time we get a picture, as we need
        // to know how big the picture is to scale the text appropriately.
    }

    @Override
    public void run() {
        AssemblyLine.OrderType lastType;
        
        // Right!  What we want to do is cycle through the queue until we run
        // into the stopper command.  take() will block the thread until there's
        // something there.
        do {
            AssemblyLine.WorkOrder curPic = null;
            try {
                // Block!
                curPic = mOrderQueue.take();
            } catch (InterruptedException e) {
                // INTERRUPTION!  Assume this means we stop.
                lastType = AssemblyLine.OrderType.END_QUEUE;
                continue;
            }
            lastType = curPic.getType();
            if(lastType == AssemblyLine.OrderType.END_QUEUE) continue;
            
            Log.d(DEBUG_TAG, "Annotator thread has an image!  It's at " + curPic.getFileLocation());
            
            // Right, we've got a picture!  Let's annotate!  First, get data.
            List<Address> addresses = null;
            
            // Keep spinning this until this isn't null.  A successful lookup
            // that has no data gives an empty list.  A failure keeps it null.
            while(addresses == null) {
                try {
                    addresses = mGeocoder.getFromLocation(
                            curPic.getGpsLocation().getLatitude(),
                            curPic.getGpsLocation().getLongitude(),
                            1);
                } catch (IOException e) {
                    e.printStackTrace();
                    // Presumably, an IOException means we can't get a data
                    // connection (or whatever the Geocoder backend communicates
                    // with is dead).  So, sleep for a bit and try it again.
                    // TODO: Find something better to do, as this doesn't
                    // allow for canceling.
                    try {
                        Thread.sleep(10000);
                    } catch (InterruptedException e1) {
                        e1.printStackTrace();
                        // What?  We were interrupted, too?!?  Geez.  Okay,
                        // fine, at this point, assume we've got nothing and
                        // don't know where we are.
                        break;
                    }
                }
            }

            // Now, let's crack that image open and get some data.
            Bitmap origBitmap = BitmapFactory.decodeFile(curPic.getFileLocation());
            Bitmap bitmap = Bitmap.createScaledBitmap(origBitmap, origBitmap.getWidth() / 2, origBitmap.getHeight() / 2, false);
            origBitmap.recycle();
            origBitmap = null;
            Canvas canvas = new Canvas(bitmap);
            
            // If we haven't made the text paint yet, we can use the bitmap to
            // decide how big to make it.
            if(mTextPaint == null) {
                // TODO: Figure this out later.  I'm assuming a 1024x768
                // picture, half the default from the Nexus One, just for
                // testing.
                mTextPaint = new Paint();
                mTextPaint.setColor(mContext.getResources().getColor(R.color.annotation_textcolor));
                mTextPaint.setTextSize(24);
                mTextPaint.setAntiAlias(true);
                mBoxHeight = 32;
                mBoxPadding = 4;
                mBoxMargin = 16;
            }
            
            drawLeftTextBox(canvas, UnitConverter.makeFullCoordinateString(mContext, curPic.getGpsLocation(), false, UnitConverter.OUTPUT_LONG), 0);
            
            Calendar date = Calendar.getInstance();
            date.setTimeInMillis(curPic.getGpsLocation().getTime());
            
            drawRightTextBox(canvas, DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(date.getTime()), 0);
            
            if(addresses != null && !addresses.isEmpty()) {
                Log.d(DEBUG_TAG, "Address retrieved, now annotating...");
                
                Address place = addresses.get(0);
                
                // FEWER DETAILS
//                drawLeftTextBox(canvas, place.getThoroughfare(), 2);
//                drawLeftTextBox(canvas, place.getLocality() + ", " + place.getAdminArea(), 1);
                
                // FULL DETAILS
                drawLeftTextBox(canvas, place.getAddressLine(0), 2);
                drawLeftTextBox(canvas, place.getAddressLine(1), 1);
            }
            
            File output = new File(curPic.getFileLocation());
            
            FileOutputStream ostream = null;
            try {
                ostream = new FileOutputStream(output);
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, ostream);
                ostream.close();
            } catch (Exception e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            bitmap.recycle();
        } while(lastType != AssemblyLine.OrderType.END_QUEUE);
        
        Log.d(DEBUG_TAG, "ENDING THREAD NOW...");
    }
    
    private void drawLeftTextBox(Canvas canvas, String text, int position) {
        Rect textBounds = new Rect();
        mTextPaint.getTextBounds(text, 0, text.length(), textBounds);
        
        int baseline = canvas.getHeight() - mBoxHeight - mBoxMargin;
        
        // DRAW!  First, a box.
        canvas.drawRect(mBoxMargin,
                baseline - (mBoxHeight * position),
                (mBoxMargin * 2) + textBounds.right,
                baseline - (mBoxHeight * (position - 1)),
                mBackgroundPaint);
        
        // Then, the text.
        canvas.drawText(text,
                mBoxMargin + mBoxPadding,
                baseline - (2 * mBoxPadding) - (mBoxHeight * (position - 1)),
                mTextPaint);
    }
    
    private void drawRightTextBox(Canvas canvas, String text, int position) {
        Rect textBounds = new Rect();
        mTextPaint.getTextBounds(text, 0, text.length(), textBounds);
        
        int baseline = canvas.getHeight() - mBoxHeight - mBoxMargin;
        
        // DRAW DRAW DRAW!
        canvas.drawRect(canvas.getWidth() - (mBoxMargin * 2) - textBounds.right,
                baseline - (mBoxHeight * position),
                canvas.getWidth() - mBoxMargin,
                baseline - (mBoxHeight * (position - 1)),
                mBackgroundPaint);
     
        // TEXT TEXT TEXT!
        canvas.drawText(text,
                canvas.getWidth() - mBoxPadding - textBounds.right - mBoxMargin,
                baseline - (2 * mBoxPadding) - (mBoxHeight * (position - 1)),
                mTextPaint);
    }

    @Override
    public String getName() {
        return "Annotator";
    }
}
