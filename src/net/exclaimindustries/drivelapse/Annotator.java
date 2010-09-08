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

import net.exclaimindustries.drivelapse.AssemblyLine.WorkOrder;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.util.Log;

/**
 * The Annotator does annotation.  Obviously.  It kicks off a thread that does
 * all modifications to the image as need be, mostly involving putting the
 * various info boxes on it.
 * 
 * @author captainspam
 */
public class Annotator extends AssemblyLine.Station {
    private static final long serialVersionUID = 1L;

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
    public void init() {
        // Annotator doesn't have any initialization to go through.
    }

    @Override
    public boolean processOrder(WorkOrder order) {
        // And away we go!
        Log.d(DEBUG_TAG, "Annotator thread has an image!  It's at " + order.getFileLocation());
        
        // Right, we've got a picture!  Let's annotate!  First, get data.
        List<Address> addresses = null;
        
        // Keep spinning this until this isn't null.  A successful lookup
        // that has no data gives an empty list.  A failure keeps it null.
        while(addresses == null) {
            if(Thread.currentThread().isInterrupted()) {
                Log.w(DEBUG_TAG, "Geocoder was interrupted, assuming this means to stop...");
                break;
            }
            try {
                addresses = mGeocoder.getFromLocation(
                        order.getGpsLocation().getLatitude(),
                        order.getGpsLocation().getLongitude(),
                        1);
            } catch (IOException e) {
                e.printStackTrace();
                if(Thread.currentThread().isInterrupted()) {
                    Log.w(DEBUG_TAG, "Geocoder was interrupted, assuming this means to stop...");
                    break;
                }
                
                Log.i(DEBUG_TAG, "Geocoder lookup failure, sleeping...");
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
                    Log.w(DEBUG_TAG, "Geocoder sleep timeout interrupted, assuming this means to stop...");
                    break;
                }
            }
        }

        // Now, let's crack that image open and get some tasty, tasty data.
        Bitmap origBitmap = BitmapFactory.decodeFile(order.getFileLocation());
        Bitmap bitmap = origBitmap.copy(origBitmap.getConfig(), true);
        origBitmap.recycle();
        origBitmap = null;
        Canvas canvas = new Canvas(bitmap);
        
        // If we haven't made the text paint yet, we can use the bitmap to
        // decide how big to make it.
        if(mTextPaint == null) {
            // TODO: Determine if I want to tweak the sizes based on the
            // size of the incoming image.
            mTextPaint = new Paint();
            mTextPaint.setColor(mContext.getResources().getColor(R.color.annotation_textcolor));
            mTextPaint.setTextSize(24);
            mTextPaint.setAntiAlias(true);
            mBoxHeight = 32;
            mBoxPadding = 4;
            mBoxMargin = 16;
            
        }
        
        drawCoordinates(canvas, order.getGpsLocation());
        drawDateAndTime(canvas, order.getGpsLocation());
        
        if(addresses != null && !addresses.isEmpty()) {
            Log.d(DEBUG_TAG, "Address retrieved, now annotating...");
            
            Address place = addresses.get(0);
            
            drawFullAddress(canvas, place);
        } else {
            // If we got here, this might mean that the addresses list is
            // empty.  However, it may also mean that it's null, meaning we
            // got interrupted during a wait.  Even though that means we're
            // bailing out, we still have an annotation to wrap up.
            drawUnknownAddress(canvas);
        }
        
        File output = new File(order.getFileLocation());
        
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
        bitmap = null;
        
        return false;
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
    
    private void drawFullAddress(Canvas canvas, Address addr) {
        drawLeftTextBox(canvas, addr.getAddressLine(0), 2);
        drawLeftTextBox(canvas, addr.getAddressLine(1), 1);
    }
    
    private void drawLessAddress(Canvas canvas, Address addr) {
        drawLeftTextBox(canvas, addr.getThoroughfare(), 2);
        drawLeftTextBox(canvas, addr.getLocality() + ", " + addr.getAdminArea(), 1);
    }
    
    private void drawUnknownAddress(Canvas canvas) {
        // This is used if the Geocoder lookup fails completely.
        drawLeftTextBox(canvas, mContext.getResources().getString(R.string.annotation_location_unknown), 1);
    }
    
    private void drawCoordinates(Canvas canvas, Location loc) {
        drawLeftTextBox(canvas, UnitConverter.makeFullCoordinateString(mContext, loc, false, UnitConverter.OUTPUT_LONG), 0);
    }
    
    private void drawDateAndTime(Canvas canvas, Location loc) {
        Calendar date = Calendar.getInstance();
        date.setTimeInMillis(loc.getTime());
        
        drawRightTextBox(canvas, DateFormat.getDateTimeInstance(DateFormat.LONG, DateFormat.MEDIUM).format(date.getTime()), 0);
    }

    @Override
    public String getName() {
        return "Annotator";
    }
}
