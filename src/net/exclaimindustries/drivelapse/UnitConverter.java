/**
 * Copyright (C)2010 Nicholas Killewald
 * 
 * This file is distributed under the terms of the BSD license.
 * The source package should have a LICENCE file at the toplevel.
 */
package net.exclaimindustries.drivelapse;

import java.text.DecimalFormat;

import android.content.Context;
//import android.content.SharedPreferences;
import android.location.Location;
import android.util.Log;

/**
 * This is a simple utility class which helps with unit conversions and 
 * formatting.
 * 
 * @author Nicholas Killewald
 */
public class UnitConverter {
    /** The number of feet per meter. */
    public static final double FEET_PER_METER = 3.2808399;
    /** The number of feet per mile. */
    public static final int FEET_PER_MILE = 5280;
    
    /** Output should be short, with fewer decimal places. */
    public static final int OUTPUT_SHORT = 0;
    /** Output should be long, with more decimal places. */
    public static final int OUTPUT_LONG = 1;
    /** Output should be even longer, with even more decimal places. */
    public static final int OUTPUT_DETAILED = 2;
    
    protected static final DecimalFormat SHORT_FORMAT = new DecimalFormat("###.000");
    protected static final DecimalFormat LONG_FORMAT = new DecimalFormat("###.00000");
    protected static final DecimalFormat DETAIL_FORMAT = new DecimalFormat("###.00000000");
    
    protected static final DecimalFormat SHORT_SECONDS_FORMAT = new DecimalFormat("###.00");
    protected static final DecimalFormat LONG_SECONDS_FORMAT = new DecimalFormat("###.0000");
    
    private static final String DEBUG_TAG = "UnitConverter";
    
    /**
     * Perform a coordinate conversion.  This will read in whatever preference
     * is currently in play (degrees, minutes, seconds) and return a string with
     * both latitude and longitude separated by a space.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param l
     *            Location to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S or E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the coordinates given
     */
    public static String makeFullCoordinateString(Context c, Location l,
            boolean useNegative, int format) {
        return makeLatitudeCoordinateString(c, l.getLatitude(), useNegative, format) + " "
            + makeLongitudeCoordinateString(c, l.getLongitude(), useNegative, format);
    }
    
    /**
     * This is the latitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lat
     *            Latitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use N/S
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the latitude of the coordinates given
     */
    public static String makeLatitudeCoordinateString(Context c, double lat,
            boolean useNegative, int format) {
        String units = "Degrees";
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lat < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lat);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "S";
            else
                return coord + "N";
        }
    }
    
    /**
     * This is the longitude half of makeFullCoordinateString.
     * 
     * @param c
     *            Context from whence the preference comes
     * @param lon
     *            Longitude to calculate
     * @param useNegative
     *            true to use positive/negative values, false to use E/W
     * @param format
     *            specify the output format using one of the OUTPUT_ statics
     * @return
     *             a string form of the longitude of the coordinates given
     */
    public static String makeLongitudeCoordinateString(Context c, double lon,
            boolean useNegative, int format) {
        String units = "Degrees";
        
        // Keep track of whether or not this is negative.  We'll attach the
        // prefix or suffix later.
        boolean isNegative = lon < 0;
        // Make this absolute so we know we won't have to juggle negatives until
        // we know what they'll wind up being.
        double rawCoord = Math.abs(lon);
        String coord;
        
        coord = makeCoordinateString(units, rawCoord, format);
        
        // Now, attach negative or suffix, as need be.
        if(useNegative) {
            if(isNegative)
                return "-" + coord;
            else
                return coord;
        } else {
            if(isNegative)
                return coord + "W";
            else
                return coord + "E";
        }
    }
    
    private static String makeCoordinateString(String units, double coord, int format) {
        // Just does the generic coordinate conversion stuff for coordinates.
        try {
            if(units.equals("Degrees")) {
                // Easy case: Use the result Location gives us, modified by the
                // longForm boolean.
                switch(format) {
                    case OUTPUT_SHORT:
                        return SHORT_FORMAT.format(coord) + "\u00b0";
                    case OUTPUT_LONG:
                        return LONG_FORMAT.format(coord) + "\u00b0";
                    default:
                        return DETAIL_FORMAT.format(coord) + "\u00b0";
                }
            } else if(units.equals("Minutes")) {
                // Harder case 1: Minutes.
                String temp = Location.convert(coord, Location.FORMAT_MINUTES);
                String[] split = temp.split(":");
                
                // Get the double form of the minutes...
                double minutes = new Double(split[1]).doubleValue();
                
                switch(format) {
                    case OUTPUT_SHORT:
                        return split[0] + "\u00b0" + SHORT_SECONDS_FORMAT.format(minutes) + "\u2032";
                    case OUTPUT_LONG:
                        return split[0] + "\u00b0" + LONG_SECONDS_FORMAT.format(minutes) + "\u2032";
                    default:
                        return split[0] + "\u00b0" + split[1]+ "\u2032";
                }
            } else if(units.equals("Seconds")) {
                // Harder case 2: Seconds.
                String temp = Location.convert(coord, Location.FORMAT_SECONDS);
                String[] split = temp.split(":");
                
                // Get the double form of the seconds...
                double seconds = new Double(split[2]).doubleValue();
                
                switch(format) {
                    case OUTPUT_SHORT:
                        return split[0] + "\u00b0" + split[1] + "\u2032" + SHORT_SECONDS_FORMAT.format(seconds) + "\u2033";
                    case OUTPUT_LONG:
                        return split[0] + "\u00b0" + split[1] + "\u2032" + LONG_SECONDS_FORMAT.format(seconds) + "\u2033";
                    default:
                        return split[0] + "\u00b0" + split[1] + "\u2032" + split[2] + "\u2033";
                }
            } else {
                return "???";
            }
        } catch (Exception ex) {
            Log.e(DEBUG_TAG, "Exception thrown during coordinate conversion: " + ex.toString());
            ex.printStackTrace();
            return "???";
        }
    }
    
//    /**
//     * Grab the current coordinate unit preference.
//     * 
//     * @param c Context from whence the preferences arise
//     * @return "Degrees", "Minutes", or "Seconds"
//     */
//    public static String getCoordUnitPreference(Context c) {
//        // Units GO!!!
//        SharedPreferences prefs = c.getSharedPreferences(
//                GHDConstants.PREFS_BASE, 0);
//        return prefs.getString(GHDConstants.PREF_COORD_UNITS, "Degrees");
//    }
}
