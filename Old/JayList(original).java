/**
*	A circular implementation of an array-based deque.
*
*	@author Jaewan Yun (Jay50@pitt.edu)
*	@version 1.0.0
*/

import java.util.*;

public class JayList<T>
{
	// underlying data structure.
	private volatile T[] jayList = null;

	// data structure settings.
	private final int DEFAULT_CAPACITY = 10;	//e.g. 1024
	private final double EXPANSION_FACTOR = 2.0;
	private final double REDUCTION_FACTOR = 2.0;
	private final int REDUCTION_REQUIREMENT_MIN = 11;	//e.g. 1025
	private final int REDUCTION_REQUIREMENT_FACTOR = 4;	//e.g. 4
	private final int MAX_CAPACITY = (2147483647 / (int) EXPANSION_FACTOR);

	// class states.
	private static volatile int concurrentObjects = 0;
	private static volatile long concurrentCapacity = 0;
	private static volatile long concurrentSize = 0;

	// array states.
	private volatile int size = 0;
	private volatile int capacity = 0;

	private volatile boolean initialized = false;

	// note that cursor does not indicate index.
	private volatile int headCursor = 0;
	private volatile int tailIndex = 0;

	/**
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public JayList()
	{
		synchronized(this)
		{
			initialized = true;
			jayList = constructArray(DEFAULT_CAPACITY);
			capacity = DEFAULT_CAPACITY;
			synchronized(this.getClass())
			{
				concurrentCapacity += DEFAULT_CAPACITY;
				concurrentObjects++;
			}
		}
	}

	/**
	*	@param capacity The desired capacity of the underlying data structure.
	*	@throws IllegalArgumentException when the size of the accepted value exceeds a predetermined maximum capacity or is less than one.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public JayList(int capacity)
	{
		synchronized(this)
		{
			initialized = true;
			jayList = constructArray(capacity);
			this.capacity = capacity;
			synchronized(this.getClass())
			{
				concurrentCapacity += capacity;
				concurrentObjects++;
			}
		}
	}

	/**
	*	@param input An array used as a template.
	*	@return true when storage was successful, and false if otherwise.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public JayList(T[] input)
	{
		synchronized(this)
		{
			initialized = true;
			setArray(input, input.length);
			synchronized(this.getClass())
			{
				concurrentObjects++;
			}
		}
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@param entry An entry to be added.
	*	@throws IllegalStateException when this has not been properly initialized or when entry cannot be added due to a predetermined maximum capacity.
	*	@throws IllegalArgumentException when entry is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T addFirst(T entry)
	{
		if(entry == null)
			throw new IllegalArgumentException();

		// add the entry to the headCursor position and increment headCursor using modulo.
		checkInitialization();
		if(isFull())
			increaseCapacity(EXPANSION_FACTOR, -1);
		jayList[headCursor] = entry;
		headCursor = (headCursor + 1) % capacity;
		size++;
		synchronized(this.getClass())
		{
			concurrentSize++;
		}

		if(headCursor == 0)
			return jayList[capacity - 1];
		else
			return jayList[headCursor - 1];
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@param entry An entry to be added.
	*	@throws IllegalStateException when this has not been properly initialized or when entry cannot be added due to a predetermined maximum capacity.
	*	@throws IllegalArgumentException when entry is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T addLast(T entry)
	{
		if(entry == null)
			throw new IllegalArgumentException();

		checkInitialization();
		if(isFull())
			increaseCapacity(EXPANSION_FACTOR, -1);

		if(tailIndex == 0)
		{
			tailIndex = capacity - 1;
			jayList[tailIndex] = entry;
		}
		else
		{
			jayList[--tailIndex] = entry;
		}
		size++;
		synchronized(this.getClass())
		{
			concurrentSize++;
		}

		return jayList[tailIndex];
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@param entry An entry to be added.
	*	@param position The relative position (not the underlying index) at which the entry will be inserted into.
	*	@throws IllegalStateException when this has not been properly initialized or when entry cannot be added due to a predetermined maximum capacity.
	*	@throws IllegalArgumentException when entry is null or if the position is invalid.
	*	@since 1.1.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T add(T entry, int position)
	{
		if(entry == null || position < 0 || position > size)
			throw new IllegalArgumentException();

		if(size < 3)
			increaseCapacity(EXPANSION_FACTOR, -1);

		checkInitialization();
		if(isFull())
		{
			increaseCapacity(EXPANSION_FACTOR, position);
			jayList[size - position] = entry;
			size++;
			synchronized(this.getClass())
			{
				concurrentSize++;
			}
		}
		else if(size == 0 || position == size)
		{
			addLast(entry);
		}
		else if(position == 0)
		{
			addFirst(entry);
		}
		else
		{
			int addIndex = ((tailIndex + capacity - 1) - position) % capacity;
			if(addIndex < size / 2)
			{
				for(int j = 0; j < position; j++)
				{
					addLast(removeFirst());
				}
				addFirst(entry);
				for(int j = 0; j < position; j++)
				{
					addFirst(removeLast());
				}
			}
			else
			{
				for(int j = 0; j < size - position; j++)
				{
					addFirst(removeLast());
				}
				addLast(entry);
				for(int j = 0; j < size - 1 - position; j++)
				{
					addLast(removeFirst());
				}
			}
		}

		return get(position);
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@return the element that was removed.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if removed value is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T removeLast()
	{
		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		// remove an item from the tailIndex and increment tailIndex using modulo.
		T toReturn = jayList[tailIndex];
		jayList[tailIndex] = null;
		tailIndex = ++tailIndex % capacity;
		size--;
		synchronized(this.getClass())
		{
			concurrentSize--;
		}

		// reduce capacity.
		if((size < (capacity / REDUCTION_REQUIREMENT_FACTOR)) && (capacity > REDUCTION_REQUIREMENT_MIN))
			decreaseCapacity(REDUCTION_FACTOR);

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@return the element that was popped.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if popped value is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T removeFirst()
	{
		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		T toReturn;
		if(headCursor == 0)
		{
			headCursor = capacity - 1;
			toReturn = jayList[headCursor];
			jayList[headCursor] = null;
		}
		else
		{
			toReturn = jayList[--headCursor];
			jayList[headCursor] = null;
		}
		size--;
		synchronized(this.getClass())
		{
			concurrentSize--;
		}

		// reduce capacity.
		if((size < (capacity / REDUCTION_REQUIREMENT_FACTOR)) && (capacity > REDUCTION_REQUIREMENT_MIN))
			decreaseCapacity(REDUCTION_FACTOR);

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@param position The relative position (not the underlying index) at which the entry will be removed from.
	*	@throws IllegalStateException when this has not been properly initialized.
	*	@throws IllegalArgumentException if the position is invalid.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if popped value is null.
	*	@since 1.1.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T remove(int position)
	{
		if(position < 0 || position >= size)
			throw new IllegalArgumentException();

		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		T toReturn = null;
		checkInitialization();
		// shift first or last positions into last or first positions until position is met, respectively, and insert at the open position.
		if(position < (size / 2))
		{
			for(int j = 0; j < position; j++)
			{
				addLast(removeFirst());
			}
			toReturn = removeFirst();
			// TODO
			for(int j = 0; j < position; j++)
			{
				addFirst(removeLast());
			}
		}
		else
		{
			for(int j = 0; j < position; j++)
			{
				addFirst(removeLast());
			}
			toReturn = removeLast();
			// TODO
			for(int j = 0; j < position; j++)
			{
				addLast(removeFirst());
			}
		}

		// reduce capacity.
		if((size < (capacity / REDUCTION_REQUIREMENT_FACTOR)) && (capacity > REDUCTION_REQUIREMENT_MIN))
			decreaseCapacity(REDUCTION_FACTOR);

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@return the element that is last.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if next value is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T getLast()
	{
		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		// get next.
		T toReturn = jayList[tailIndex];

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@return the element that is first.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if next value is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T getFirst()
	{
		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		// get next.
		T toReturn;

		if(headCursor == 0)
			toReturn = jayList[capacity - 1];
		else
			toReturn = jayList[headCursor - 1];

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	Bottleneck synchronized with this.
	*
	*	@param position The entry to return at this relative position.
	*	@return the element at the relative position.
	*	@throws NoSuchElementException if data structure is empty.
	*	@throws NullPointerException if the value is null.
	*	@throws IllegalArgumentException if the position is invalid.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized T get(int position)
	{
		if(position < 0 || position >= size)
			throw new IllegalArgumentException();

		// check that data structure is non-empty
		if(isEmpty())
			throw new NoSuchElementException();

		T toReturn = null;
		int relativePosition = (tailIndex + size - position - 1) % capacity;
		toReturn = jayList[relativePosition];

		if(toReturn == null)
			throw new NullPointerException();

		return toReturn;
	}

	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@param factor The multiplicative expansion coefficient.
	*	@throws IllegalArgumentException when capacity cannot increase due to a predetermined maximum capacity.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private void increaseCapacity(double factor, int position)
	{
		// increase capacity.
		if((int) (capacity * EXPANSION_FACTOR + 1) > MAX_CAPACITY)
			throw new IllegalStateException();

		synchronized(this.getClass())
		{
			concurrentCapacity -= capacity;
		}
		int originalCapacity = capacity;
		capacity = (int) (capacity * factor);
		synchronized(this.getClass())
		{
			concurrentCapacity += capacity;
		}
		T[] temporaryRef = constructArray(capacity);
		if(position == -1)// || position == (size - 1))
		{
			for(int j = 0; j < size; j++)
			{
				temporaryRef[j] = jayList[tailIndex % originalCapacity];
				tailIndex++;
			}
			tailIndex = 0;
			headCursor = size;
			jayList = temporaryRef;
		}
		else
		{
			position = size - position;
			for(int j = 0; j <= size; j++)
			{
				if(j == position)
				{
					if(tailIndex == 0)
						tailIndex = capacity - 1;
					else
						tailIndex--;
				}
				temporaryRef[j] = jayList[tailIndex % originalCapacity];
				tailIndex++;
			}
			tailIndex = 0;
			headCursor = size + 1;
			jayList = temporaryRef;
		}
	}

	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@param factor The multiplicative reduction coefficient.
	*	@throws IllegalArgumentException when capacity cannot increase due to a predetermined maximum capacity.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private void decreaseCapacity(double factor)
	{
		// decrease capacity.
		int originalCapacity = capacity;
		synchronized(this.getClass())
		{
			concurrentCapacity -= capacity;
		}
		capacity = (int) (capacity / factor);
		synchronized(this.getClass())
		{
			concurrentCapacity += capacity;
		}
		T[] temporaryRef = constructArray(capacity);
		for(int j = 0; j < capacity - 1; j++)
		{
			temporaryRef[j] = jayList[tailIndex++ % originalCapacity];
		}
		tailIndex = 0;
		headCursor = size;
		jayList = temporaryRef;
	}

	/**
	*	@return true if values were cleared; false if no valued exists.
	*	@throws IllegalStateException when this has not been properly initialized.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized boolean clear()
	{
		checkInitialization();

		if(isEmpty())
			return false;

		jayList = null;
		jayList = constructArray(DEFAULT_CAPACITY);
		synchronized(this.getClass())
		{
			concurrentCapacity -= (capacity - DEFAULT_CAPACITY);

		}
		capacity = DEFAULT_CAPACITY;
		synchronized(this.getClass())
		{
			concurrentSize -= size;
			size = 0;
		}
		headCursor = 0;
		tailIndex = 0;

		return true;
	}

	/**
	*	@param input An array to be used as a template.
	*	@return true if storage was successful, and false if otherwise.
	*	@throws IllegalStateException if this has not been properly initialized.
	*	@throws IllegalArgumentException if capacity cannot increase due to a predetermined maximum capacity.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized boolean setArray(T[] input)
	{
		return setArray(input, input.length);
	}
	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@param input An array used as a template.
	*	@return true if storage was successful, and false if otherwise.
	*	@throws IllegalStateException if this has not been properly initialized.
	*	@throws IllegalArgumentException if capacity cannot increase due to a predetermined maximum capacity.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private boolean setArray(T[] input, int length)
	{
		checkInitialization();

		if(input == null)
			return false;

		if(length + 1 > MAX_CAPACITY)
			throw new IllegalArgumentException();

		synchronized(this.getClass())
		{
			concurrentCapacity -= jayList.length;
		}

		jayList = constructArray(length + 1);
		capacity = length + 1;

		synchronized(this.getClass())
		{
			concurrentCapacity += length + 1;
		}

		// copy references
		synchronized(this.getClass())
		{
			concurrentSize -= size;
			size = 0;
		}
		for(int j = 0; j < length; j++)
		{
			if(input[j] != null)
			{
				jayList[(length - 1) - j] = input[j];
				size++;
			}
		}
		synchronized(this.getClass())
		{
			concurrentSize += size;
		}
		tailIndex = 0;
		headCursor = length;
		return true;
	}

	/**
	*	@return a copy of this array.
	*	@throws IllegalStateException when this has not been properly initialized.
	*	@throws NullPointerException when jayList is null.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	@SuppressWarnings("unchecked") public synchronized T[] toArray()
	{
		checkInitialization();
		int newTailIndex = tailIndex;
		T[] toReturn = (T[]) new Object[size];
		for(int j = size - 1; j >= 0; j--)
		{
			toReturn[j] = jayList[newTailIndex++ % capacity];
		}
		return toReturn;
	}

	/**
	*	@param toCopy An array used as a template.
	*	@return a copy of the accepted array.
	*	@throws NullPointerException when the accepted array is null.
	*	@throws IllegalArgumentException when the size of the accepted array exceeds a predetermined maximum capacity.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	@SuppressWarnings("unchecked") private T[] copyOf(T[] toCopy)
	{
		synchronized(this)
		{
			if(toCopy == null)
				throw new NullPointerException();

			if(toCopy.length > MAX_CAPACITY)
				throw new IllegalArgumentException();

			// copy the accepted array
			T[] toReturn = (T[]) new Object[toCopy.length];
			for(int j = 0; j < toCopy.length; j++)
			{
				toReturn[j] = toCopy[j];
			}
			return toReturn;
		}
	}

	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@param capacity The capacity of the array to be constructed.
	*	@return initialized array of T types with the accepted value as its capacity.
	*	@throws IllegalArgumentException when the size of the accepted value exceeds a predetermined maximum capacity.
	*	@throws IllegalArgumentException when the size of the accepted value is less than one.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	@SuppressWarnings("unchecked") private T[] constructArray(int capacity)
	{
		if(capacity > MAX_CAPACITY || capacity < 1)
			throw new IllegalArgumentException();

		// initialize an array of type T
		T[] toReturn = (T[]) new Object[capacity];

		// setting the states
		initialized = true;
		return toReturn;
	}

	/**
	*	All client methods need to ensure object types are comparable.
	*
	*	@return true if sort was successful; false if no values exists.
	*	@throws UnsupportedOperationException if object types are not comparable.
	*	@since 1.1.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized boolean sort()
	{
		if(isEmpty())
			return false;

		try
		{
			T[] a = toArray();
			Arrays.sort(a);
			setArray(a);
			return true;
		}
		catch(Exception e)
		{
			throw new UnsupportedOperationException();
		}
	}

	/**
	*	@return number of elements contained within this data structure.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized int length()
	{
		return size;
	}

	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@throws IllegalStateException when this has not been properly initialized.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private void checkInitialization()
	{
		if(!initialized)
			throw new IllegalStateException();
	}

	/**
	*	@return true if no elements exist in this data structure.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized boolean isEmpty()
	{
		return headCursor == tailIndex;
	}

	/**
	*	All client methods need to ensure synchronization with this.
	*
	*	@return true if data represented is in full state.
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private boolean isFull()
	{
		 return ((headCursor + 1) % capacity) == tailIndex;
	}

	/**
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	protected void finalize()
	{
		synchronized(this)
		{
			initialized = false;
			synchronized(this.getClass())
			{
				concurrentObjects--;
				concurrentCapacity -= capacity;
			}
		}
	}

	/**
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized String toString()
	{
		return Arrays.toString(jayList);
	}

	/**
	*	@param keyword Keyword that the method body portion execution is dependent on
	*	@since 1.1.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	public synchronized void showState()
	{
		print(1, "jayList Address :\t" + jayList);
		print(1, "MAX_CAPACITY :\t\t" + MAX_CAPACITY);
		print(1, "DEFAULT_CAPACITY :\t" + DEFAULT_CAPACITY);
		print(1, "EXPANSION_FACTOR :\t" + EXPANSION_FACTOR);
		print(1, "REDUCTION_FACTOR :\t" + REDUCTION_FACTOR);
		print(1, "concurrentObjects :\t" + concurrentObjects);
		print(1, "concurrentSize :\t" + concurrentSize + "\t<---A");
		print(1, "length between indices :" + (((capacity - tailIndex) + headCursor) % capacity) + "\t<---A");
		print(1, "size :\t\t\t" + size + "\t<---A");
		print(1, "concurrentCapacity :\t" + concurrentCapacity + "\t<---B");
		print(1, "capacity :\t\t" + capacity + "\t<---B");
		print(1, "initialized :\t\t" + initialized);
		print(1, "headCursor :\t\t" + headCursor);
		print(1, "tailIndex :\t\t" + tailIndex);
		print(1, "\n\tEND OF JayList EXPLICIT STATE\n");

		if(jayList != null)
		{
			print(1, "length :\t\t" + jayList.length);
			if(jayList[tailIndex] != null)
				print(1, "tailIndex type :\t" + jayList[tailIndex].getClass().toString());
			else
				print(1, "tailIndex type :\tnull");
			if(jayList[headCursor] != null)
				print(1, "headCursor type :\t" + jayList[tailIndex].getClass().toString());
			else
				print(1, "headCursor type :\tnull");
			if(headCursor - 1 < 0)
				if(jayList[capacity - 1] != null)
					print(1, "headIndex type :\t" + jayList[tailIndex].getClass().toString());
			if(headCursor - 1 >= 0)
				if(jayList[headCursor - 1] != null)
					print(1, "headIndex type :\t" + jayList[tailIndex].getClass().toString());
			print(1, "\n\tEND OF T[] EXPLICIT STATE\n");

			for(int j = 0; j < jayList.length; j++)
			{
				print(0, "Index  " + j + ": \t[" + jayList[j]);
				if(jayList[j] != null)
					print(1, "\t] of type (" + jayList[j].getClass().toString() + ")");
				else
					print(0, "\t]\n");
			}
			print(1, "\n\tEND OF T[] ENUMERATION");
		}
		else
		{
			print(2, "jayList is null therefore unaccessible");
		}
	}

	/**
	*	@since 1.0.0
	*	@author Jaewan Yun (Jay50@pitt.edu)
	*/
	private void print(int skip, String toPrint)
	{
		System.out.print(toPrint);

		if(skip == 0)
			return;

		for(int j = 0; j < skip; j++)
		{
			System.out.print("\n");
		}
	}
}