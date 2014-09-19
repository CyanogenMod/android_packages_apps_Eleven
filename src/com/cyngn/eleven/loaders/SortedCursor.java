/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.loaders;

import android.database.AbstractCursor;
import android.database.Cursor;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * This cursor basically wraps a song cursor and is given a list of the order of the ids of the
 * contents of the cursor. It wraps the Cursor and simulates the internal cursor being sorted
 * by moving the point to the appropriate spot
 */
public class SortedCursor extends AbstractCursor {
    // cursor to wrap
    private final Cursor mCursor;
    // the map of external indices to internal indices
    private ArrayList<Integer> mOrderedPositions;

    /**
     * @param cursor to wrap
     * @param order the list of ids in sorted order to display
     * @param columnName the column name of the id to look up in the internal cursor
     */
    public SortedCursor(final Cursor cursor, final long[] order, final String columnName) {
        if (cursor == null) {
            throw new IllegalArgumentException("Non-null cursor is needed");
        }

        mCursor = cursor;
        buildCursorPositionMapping(order, columnName);
    }

    /**
     * This function populates mOrderedPositions with the cursor positions in the order based
     * on the order passed in
     * @param order the target order of the internal cursor
     */
    private void buildCursorPositionMapping(final long[] order, final String columnName) {
        mOrderedPositions = new ArrayList<Integer>(mCursor.getCount());

        HashMap<Long, Integer> mapCursorPositions = new HashMap<Long, Integer>(mCursor.getCount());
        final int idPosition = mCursor.getColumnIndex(columnName);

        if (mCursor.moveToFirst()) {
            // first figure out where each of the ids are in the cursor
            do {
                mapCursorPositions.put(mCursor.getLong(idPosition), mCursor.getPosition());
            } while (mCursor.moveToNext());

            // now create the ordered positions to map to the internal cursor given the
            // external sort order
            for (long id : order) {
                if (mapCursorPositions.containsKey(id)) {
                    mOrderedPositions.add(mapCursorPositions.get(id));
                }
            }
        }
    }

    @Override
    public void close() {
        mCursor.close();

        super.close();
    }

    @Override
    public int getCount() {
        return mOrderedPositions.size();
    }

    @Override
    public String[] getColumnNames() {
        return mCursor.getColumnNames();
    }

    @Override
    public String getString(int column) {
        return mCursor.getString(column);
    }

    @Override
    public short getShort(int column) {
        return mCursor.getShort(column);
    }

    @Override
    public int getInt(int column) {
        return mCursor.getInt(column);
    }

    @Override
    public long getLong(int column) {
        return mCursor.getLong(column);
    }

    @Override
    public float getFloat(int column) {
        return mCursor.getFloat(column);
    }

    @Override
    public double getDouble(int column) {
        return mCursor.getDouble(column);
    }

    @Override
    public boolean isNull(int column) {
        return mCursor.isNull(column);
    }

    @Override
    public boolean onMove(int oldPosition, int newPosition) {
        if (newPosition >= 0 && newPosition < getCount()) {
            mCursor.moveToPosition(mOrderedPositions.get(newPosition));
            return true;
        }

        return false;
    }
}
