package net.florianx.jperfcounters.core;

import java.util.Arrays;

import org.HdrHistogram.Histogram;

@SuppressWarnings("unchecked")
class Ring<T> {

	private Object[] storage;
	private long head;
	private T toRecycle1;
	private T toRecycle2;
	
	public Ring(int maxEntries) {
		storage = new Object[maxEntries];
		head = 0;
	}

	public void add(T val) {
		int s = (int)(head % storage.length);
		toRecycle2 = toRecycle1;
		toRecycle1 = (T)storage[s];
		storage[s] = val;
		++head;
	}

//	public void reset() {
//		head = 0;
//		for (int i = 0; i < storage.length; i++) {
//			if (storage[i] != null) {
//				((T)storage[i]).reset();
//			}
//		}
//	}

	/** returns -1 if no slots at all */
	public long getLastSlot() {
		return head - 1;
	}
	
	public T getLastEntry() {
		int s = (int)((head - 1 + storage.length) % storage.length);
		return (T)storage[s];
	}
	
	public T getEntry(long slot) {
		return (T)storage[(int)(slot % storage.length)];
	}

	/**
	 * Returns the number of entries that are returned.
	 * The slots
	 * are returned in the buffer in reverse order of slot number.
	 * If not enough slots are available, last cells of buffer
	 * will be set to null.
	 * The endSlot must be an existing slot, not one from the future.
	 * Return
	 */
	public int getEntries(T[] buffer, long endSlot) {
		if (endSlot < head - storage.length)  {
			Arrays.fill(buffer, null);
			return 0;
		}
		if (endSlot > head - 1) {
			endSlot = head - 1;
		}
		int b = 0;
		int normSlot = (int)(endSlot % storage.length);
		int normNextSlot = (int)((endSlot + 1) % storage.length);
		for (int i = normSlot; i >= 0 && b < buffer.length; i--) {
			buffer[b++] = (T)storage[i];
		}
		if (head > storage.length) { // if we've already wrapped around
			for (int i = storage.length - 1; i >= normNextSlot && b < buffer.length; i--) {
				buffer[b++] = (T)storage[i];
			}
		}
		int retSize = b;
		// complete with nulls
		while (b < buffer.length) {
			buffer[b++] = null;
		}
if (retSize > 0 && buffer[0] == null) {
	throw new RuntimeException("null!");
}
		return retSize;
	}
	
	public T getRecycled() {
		T r;
		if (toRecycle2 != null) {
			r = toRecycle2;
			toRecycle2 = null;
		} else {
			r = toRecycle1;
			toRecycle1 = null;
		}
		return r;
	}
	
	public String toString() {
		return "[head=" + head + "]";
	}

}