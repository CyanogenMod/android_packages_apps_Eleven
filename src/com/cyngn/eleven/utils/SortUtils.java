package com.cyngn.eleven.utils;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.TreeMap;

/**
 * Implementation of custom sorting routines of song list
 */
public class SortUtils {

    /**
     * Sorts items based on the localized bucket letter they belong to and the sort order specified
     * @param items the original list of items
     * @param sortOrder values derived from SortOrder.class
     * @return the new sorted list
     */
    public static <T> ArrayList<T> localizeSortList(ArrayList<T> items, String sortOrder) {
        ArrayList<T> finalList = Lists.newArrayList();
        // map of items grouped by their localized label
        TreeMap<String, LinkedList<T>> mappedList = new TreeMap<String, LinkedList<T>>();

        // list holding items that don't have a localized label
        ArrayList<T> nonLocalizableItems = Lists.newArrayList();

        for (T item : items) {
            // get the bucket letter based on the attribute to sort by
            String label = MusicUtils.getLocalizedBucketLetterByAttribute(item, sortOrder);
            //divvy items based on their localized bucket letter
            if (label != null) {
                if (mappedList.get(label) == null) {
                    // create new label slot to assign items
                    mappedList.put(label, Lists.<T>newLinkedList());
                }
                // add item to the label's list
                mappedList.get(label).add(item);
            } else {
                nonLocalizableItems.add(item);
            }
        }

        // generate a sorted item list out of localizable items
        boolean isDescendingSort = MusicUtils.isSortOrderDesending(sortOrder);
        finalList.addAll(getSortedList(mappedList, isDescendingSort));
        finalList.addAll(nonLocalizableItems);

        return finalList;
    }

    /**
     * Traverses a tree map of a divvied up list to generate a sorted list
     * @param mappedList the bucketized list of items based on the header
     * @param reverseOrder dictates the order in which the TreeMap is traversed (descending order
     *                     if true)
     * @return the combined sorted list
     */
    private static <T> ArrayList<T> getSortedList(TreeMap<String, LinkedList<T>> mappedList,
                                                        boolean reverseOrder) {
        ArrayList<T> sortedList = Lists.newArrayList();

        Iterator<String> iterator = mappedList.navigableKeySet().iterator();
        if (reverseOrder) {
            iterator = mappedList.navigableKeySet().descendingIterator();
        }

        while (iterator.hasNext()) {
            LinkedList<T> list = mappedList.get(iterator.next());
            sortedList.addAll(list);
        }

        return sortedList;
    }

}
