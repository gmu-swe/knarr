package edu.gmu.swe.knarr.internal;


import java.io.Externalizable;
import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectOutput;

public final class ByteArrayList implements Externalizable {
    private static final long serialVersionUID = 7110944498336814100L;
    private int size = 0;
    private byte[] elements;

    /**
     * Constructs a new list with an initial capacity of 10.
     */
    public ByteArrayList() {
        this(10);
    }

    /**
     * Constructs a new list with the specified initial capacity.
     *
     * @param capacity the initial capacity of this list.
     * @throws IllegalArgumentException if capacity is less than 0
     */
    public ByteArrayList(int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException();
        }
        elements = new byte[capacity];
    }

    /**
     * Constructs a copy of the specified list
     *
     * @param list the list to be copied
     * @throws NullPointerException if list is null
     */
    public ByteArrayList(ByteArrayList list) {
        this.size = list.size;
        this.elements = new byte[list.elements.length];
        System.arraycopy(list.elements, 0, elements, 0, list.elements.length);
    }

    /**
     * Adds the specified element to the end of this list.
     *
     * @param element the element to add.
     */
    public void add(byte element) {
        if (size == elements.length) {
            grow(size + 1);
        }
        elements[size++] = element;
    }

    /**
     * @param index index of the element to return
     * @return the element at the specified position in this list
     * @throws IndexOutOfBoundsException if the index is out of range {@code index < 0 || index >= size()}
     */
    public byte get(int index) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        return elements[index];
    }

    private void grow(int minCapacity) {
        int oldCapacity = elements.length;
        int newCapacity = oldCapacity + (oldCapacity >> 1);
        if (newCapacity - minCapacity < 0) {
            newCapacity = minCapacity;
        }
        if (newCapacity - (Integer.MAX_VALUE - 8) > 0) {
            newCapacity = Integer.MAX_VALUE - 8;
        }
        byte[] temp = elements;
        elements = new byte[newCapacity];
        System.arraycopy(temp, 0, elements, 0, temp.length);
    }

    /**
     * @return true if this list contains no elements
     */
    public boolean isEmpty() {
        return size == 0;
    }

    /**
     * @return the number of elements in this list
     */
    public int size() {
        return size;
    }

    /**
     * Cuts the size of this list to the specified size removing any elements at indices greater than or equal to the
     * specified size.
     *
     * @param size the size that the list is to be cut to
     * @throws IllegalArgumentException if the specified size is larger than the current size of this list
     */
    public void trim(int size) {
        if (size > this.size) {
            throw new IllegalArgumentException();
        }
        if (size != this.size) {
            this.size = size;
            byte[] temp = elements;
            elements = new byte[size];
            System.arraycopy(temp, 0, elements, 0, elements.length);
        }
    }

    public void reset(){
        this.size = 0;
    }

    /**
     * Replaces the element at the specified position in this list with the
     * specified element.
     *
     * @param index   index of the element to replace
     * @param element element to be stored at the specified position
     * @return the element previously at the specified position
     * @throws IndexOutOfBoundsException if the index is out of range {@code index < 0 || index >= size()}
     */
    public byte set(int index, byte element) {
        if (index < 0 || index >= size) {
            throw new IndexOutOfBoundsException();
        }
        byte result = elements[index];
        elements[index] = element;
        return result;
    }

    @Override
    public String toString() {
        StringBuilder buffer = new StringBuilder(size() * 16).append('[');
        for (int i = 0; i < size(); i++) {
            buffer.append(get(i));
            if (i != size() - 1) {
                buffer.append(", ");
            }
        }
        return buffer.append(']').toString();
    }

    public byte[] getBytesUnsafe(){
        return this.elements;
    }

    @Override
    public void writeExternal(ObjectOutput out) throws IOException {
        out.write(size);
        out.writeInt(elements.length);
        out.write(elements);
    }

    @Override
    public void readExternal(ObjectInput in) throws IOException {
        this.size = in.read();
        int length = in.readInt();
        this.elements = new byte[length];
        in.readFully(this.elements);
    }
}

