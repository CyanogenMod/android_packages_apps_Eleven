/*
 * Copyright (C) 2014 Cyanogen, Inc.
 */
package com.cyngn.eleven.utils;

import android.content.Context;

import com.cyngn.eleven.R;
import com.cyngn.eleven.model.Album;
import com.cyngn.eleven.model.Artist;
import com.cyngn.eleven.model.Song;

import java.util.List;
import java.util.TreeMap;

/**
 * This Utils class contains code that compares two different items and determines whether
 * a section should be created
 */
public class SectionCreatorUtils {
    /**
     * Interface to compare two items and create labels
     * @param <T> type of item to compare
     */
    public static interface IItemCompare<T> {
        /**
         * Compares to items and returns a section divider T if there should
         * be a section divider between first and second
         * @param first the first element in the list.  If null, it is checking to see
         *              if we need a divider at the beginning of the list
         * @param second the second element in the list.
         * @return String the expected separator label or null if none
         */
        public String createSectionSeparator(T first, T second);

        /**
         * Returns the section label that corresponds to this item
         * @param item the item
         * @return the section label that this label falls under
         */
        public String createLabel(T item);
    }


    /**
     * A localized String comparison implementation of IItemCompare
     * @param <T> the type of item to compare
     */
    public static abstract class LocalizedCompare<T> implements IItemCompare<T> {
        @Override
        public String createSectionSeparator(T first, T second) {
            String secondLabel = createLabel(second);
            if (first == null || !createLabel(first).equals(secondLabel)) {
                return createLabel(second);
            }

            return null;
        }

        @Override
        public String createLabel(T item) {
            return MusicUtils.getLocalizedBucketLetter(getString(item), trimName());
        }

        /**
         * @return true if we want to trim the name first - apparently artists don't trim
         * but albums/songs do
         */
        public boolean trimName() {
            return true;
        }

        public abstract String getString(T item);
    }

    /**
     * A simple int comparison implementation of IItemCompare
     * @param <T> the type of item to compare
     */
    public static abstract class IntCompare<T> implements IItemCompare<T> {
        @Override
        public String createSectionSeparator(T first, T second) {
            if (first == null || getInt(first) != getInt(second)) {
                return createLabel(second);
            }

            return null;
        }

        @Override
        public String createLabel(T item) {
            return String.valueOf(getInt(item));
        }

        public abstract int getInt(T item);
    }

    /**
     * A Bounded int comparison implementation of IntCompare
     * Basically this will take ints and determine what bounds it falls into
     * For example, 1-5 mintes, 5-10 minutes, 10+ minutes
     * @param <T> the type of item to compare
     */
    public static abstract class BoundedIntCompare<T> extends IntCompare<T> {
        protected Context mContext;

        public BoundedIntCompare(Context context) {
            mContext = context;
        }

        protected abstract int getStringId(int value);

        @Override
        public String createSectionSeparator(T first, T second) {
            int secondStringId = getStringId(getInt(second));
            if (first == null || getStringId(getInt(first)) != secondStringId) {
                return createLabel(secondStringId, second);
            }

            return null;
        }

        protected String createLabel(int stringId, T item) {
            return mContext.getString(stringId);
        }

        @Override
        public String createLabel(T item) {
            return createLabel(getStringId(getInt(item)), item);
        }
    }

    /**
     * This implements BoundedIntCompare and gives duration buckets
     * @param <T> the type of item to compare
     */
    public static abstract class DurationCompare<T> extends BoundedIntCompare<T> {
        private static final int SECONDS_PER_MINUTE = 60;

        public DurationCompare(Context context) {
            super(context);
        }

        @Override
        protected int getStringId(int value) {
            if (value < 30) {
                return R.string.header_less_than_30s;
            } else if (value < 1 * SECONDS_PER_MINUTE) {
                return R.string.header_30_to_60_seconds;
            } else if (value < 2 * SECONDS_PER_MINUTE) {
                return R.string.header_1_to_2_minutes;
            } else if (value < 3 * SECONDS_PER_MINUTE) {
                return R.string.header_2_to_3_minutes;
            } else if (value < 4 * SECONDS_PER_MINUTE) {
                return R.string.header_3_to_4_minutes;
            } else if (value < 5 * SECONDS_PER_MINUTE) {
                return R.string.header_4_to_5_minutes;
            } else if (value < 10 * SECONDS_PER_MINUTE) {
                return R.string.header_5_to_10_minutes;
            } else if (value < 30 * SECONDS_PER_MINUTE) {
                return R.string.header_10_to_30_minutes;
            } else if (value < 60 * SECONDS_PER_MINUTE) {
                return R.string.header_30_to_60_minutes;
            }

            return R.string.header_greater_than_60_minutes;
        }
    }

    /**
     * This implements BoundedIntCompare and gives number of songs buckets
     * @param <T> the type of item to compare
     */
    public static abstract class NumberOfSongsCompare<T> extends BoundedIntCompare<T> {
        public NumberOfSongsCompare(Context context) {
            super(context);
        }

        @Override
        protected int getStringId(int value) {
            if (value <= 1) {
                return R.string.header_1_song;
            } else if (value <= 4) {
                return R.string.header_2_to_4_songs;
            } else if (value <= 9) {
                return R.string.header_5_to_9_songs;
            }

            return R.string.header_10_plus_songs;
        }
    }

    /**
     * This implements BoundedIntCompare and gives number of albums buckets
     * @param <T> the type of item to compare
     */
    public static abstract class NumberOfAlbumsCompare<T> extends BoundedIntCompare<T> {
        public NumberOfAlbumsCompare(Context context) {
            super(context);
        }

        @Override
        protected int getStringId(int value) {
            if (value <= 1) {
                return R.string.header_1_album;
            } else if (value <= 4) {
                return R.string.header_n_albums;
            }

            return R.string.header_5_plus_albums;
        }

        @Override
        public String createSectionSeparator(T first, T second) {
            boolean returnSeparator = false;
            if (first == null) {
                returnSeparator = true;
            } else {
                // create a separator if both album counts are different and they are
                // not greater than 5 albums
                int firstInt = getInt(first);
                int secondInt = getInt(second);
                if (firstInt != secondInt && 
                        !(firstInt >= 5 && secondInt >= 5)) {
                    returnSeparator = true;
                }
            }

            if (returnSeparator) {
                return createLabel(second);
            }

            return null;
        }

        @Override
        protected String createLabel(int stringId, T item) {
            if (stringId == R.string.header_n_albums) {
                return mContext.getString(stringId, getInt(item));
            }

            return super.createLabel(stringId, item);
        }
    }

    /**
     * This creates the sections give a list of items and the comparison algorithm
     * @param list The list of items to analyze
     * @param comparator The comparison function to use
     * @param <T> the type of item to compare
     * @return Creates a TreeMap of indices (if the headers were part of the list) to section labels
     */
    public static <T> TreeMap<Integer, String> createSections(final List<T> list,
                                                              final IItemCompare<T> comparator) {
        if (list != null && list.size() > 0) {
            TreeMap<Integer, String> sectionHeaders = new TreeMap<Integer, String>();
            for (int i = 0; i < list.size(); i++) {
                T first = (i == 0 ? null : list.get(i - 1));
                T second = list.get(i);

                String separator = comparator.createSectionSeparator(first, second);
                if (separator != null) {
                    // add sectionHeaders.size() to store the indices of the combined list
                    sectionHeaders.put(sectionHeaders.size() + i, separator);
                }
            }

            return sectionHeaders;
        }

        return null;
    }

    /**
     * Returns an artist comparison based on the current sort
     * @param context Context for string generation
     * @return the artist comparison method
     */
    public static IItemCompare<Artist> createArtistComparison(final Context context) {
        IItemCompare<Artist> sectionCreator = null;

        String sortOrder = PreferenceUtils.getInstance(context).getArtistSortOrder();
        if (sortOrder.equals(SortOrder.ArtistSortOrder.ARTIST_A_Z)
                || sortOrder.equals(SortOrder.ArtistSortOrder.ARTIST_Z_A)) {
            sectionCreator = new SectionCreatorUtils.LocalizedCompare<Artist>() {
                @Override
                public String getString(Artist item) {
                    return item.mArtistName;
                }
            };
        } else if (sortOrder.equals(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_ALBUMS)) {
            sectionCreator = new SectionCreatorUtils.NumberOfAlbumsCompare<Artist>(context) {
                @Override
                public int getInt(Artist item) {
                    return item.mAlbumNumber;
                }
            };
        } else if (sortOrder.equals(SortOrder.ArtistSortOrder.ARTIST_NUMBER_OF_SONGS)) {
            sectionCreator = new NumberOfSongsCompare<Artist>(context) {
                @Override
                public int getInt(Artist item) {
                    return item.mSongNumber;
                }
            };
        }

        return sectionCreator;
    }

    /**
     * Returns an album comparison based on the current sort
     * @param context Context for string generation
     * @return the album comparison method
     */
    public static IItemCompare<Album> createAlbumComparison(final Context context) {
        IItemCompare<Album> sectionCreator = null;

        String sortOrder = PreferenceUtils.getInstance(context).getAlbumSortOrder();
        if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_A_Z)
                || sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_Z_A)) {
            sectionCreator = new LocalizedCompare<Album>() {
                @Override
                public String getString(Album item) {
                    return item.mAlbumName;
                }
            };
        } else if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_ARTIST)) {
            sectionCreator = new LocalizedCompare<Album>() {
                @Override
                public String getString(Album item) {
                    return item.mArtistName;
                }

                @Override
                public boolean trimName() {
                    return false;
                }
            };
        } else if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_NUMBER_OF_SONGS)) {
            sectionCreator = new NumberOfSongsCompare<Album>(context) {
                @Override
                public int getInt(Album item) {
                    return item.mSongNumber;
                }
            };
        } else if (sortOrder.equals(SortOrder.AlbumSortOrder.ALBUM_YEAR)) {
            sectionCreator = new IntCompare<Album>() {
                private static final int INVALID_YEAR = -1;

                @Override
                public int getInt(Album item) {
                    // if we don't have a year, treat it as invalid
                    if (item.mYear == null) {
                        return INVALID_YEAR;
                    }

                    int year = Integer.valueOf(item.mYear);

                    // if the year is extremely low, treat it as invalid too
                    if (MusicUtils.isInvalidYear(year)) {
                        return INVALID_YEAR;
                    }

                    return year;
                }

                @Override
                public String createLabel(Album item) {
                    if (MusicUtils.isInvalidYear(getInt(item))) {
                        return context.getString(R.string.header_unknown_year);
                    }

                    return item.mYear;
                }
            };
        }

        return sectionCreator;
    }

    /**
     * Returns an song comparison based on the current sort
     * @param context Context for string generation
     * @return the song comparison method
     */
    public static IItemCompare<Song> createSongComparison(final Context context) {
        IItemCompare<Song> sectionCreator = null;

        String sortOrder = PreferenceUtils.getInstance(context).getSongSortOrder();

        // doesn't make sense to have headers for SONG_FILENAME
        // so we will not return a sectionCreator for that one
        if (sortOrder.equals(SortOrder.SongSortOrder.SONG_A_Z)
                || sortOrder.equals(SortOrder.SongSortOrder.SONG_Z_A)) {
            sectionCreator = new LocalizedCompare<Song>() {
                @Override
                public String getString(Song item) {
                    return item.mSongName;
                }
            };
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_ALBUM)) {
            sectionCreator = new LocalizedCompare<Song>() {
                @Override
                public String getString(Song item) {
                    return item.mAlbumName;
                }
            };
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_ARTIST)) {
            sectionCreator = new LocalizedCompare<Song>() {
                @Override
                public String getString(Song item) {
                    return item.mArtistName;
                }

                @Override
                public boolean trimName() {
                    return false;
                }
            };
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_DURATION)) {
            sectionCreator = new DurationCompare<Song>(context) {
                @Override
                public int getInt(Song item) {
                    return item.mDuration;
                }
            };
        } else if (sortOrder.equals(SortOrder.SongSortOrder.SONG_YEAR)) {
            sectionCreator = new SectionCreatorUtils.IntCompare<Song>() {
                @Override
                public int getInt(Song item) {
                    return item.mYear;
                }

                @Override
                public String createLabel(Song item) {
                    // I have seen tracks in my library where it would return 0 or 2
                    // so have this check to return a more friendly label in that case
                    if (MusicUtils.isInvalidYear(item.mYear)) {
                        return context.getString(R.string.header_unknown_year);
                    }

                    return super.createLabel(item);
                }
            };
        }

        return sectionCreator;
    }
}
