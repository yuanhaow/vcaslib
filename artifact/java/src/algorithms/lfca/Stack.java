/*
 *  Copyright 2018 Kjell Winblad (kjellwinblad@gmail.com, http://winsh.me)
 *
 *  This file is part of JavaRQBench
 *
 *  catrees is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  catrees is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with catrees.  If not, see <http://www.gnu.org/licenses/>.
 */


package algorithms.lfca;

class Stack<T> {
    private int stackSize;
    private int stackPos = 0;
    private Object[] stackArray;
    
    public Stack(int stackSize){
        this.stackSize = stackSize;
        stackArray = new Object[stackSize];
    }

    public Stack(){
        this(32);
    }
    public void push(T node){
        if(stackPos == stackSize){
            int newStackSize = stackSize*4;
        	Object[] newStackArray = new Object[newStackSize];
            for(int i = 0; i < stackSize;i++){
                newStackArray[i] = stackArray[i];
            }
            stackSize = newStackSize;
            stackArray = newStackArray;
        }
        stackArray[stackPos] = node;
        stackPos = stackPos + 1;
    }
    @SuppressWarnings("unchecked")
	public T pop(){
        if(stackPos == 0){
            return null;
        }
        stackPos = stackPos - 1;
        return (T)stackArray[stackPos];
    }
    @SuppressWarnings("unchecked")
	public T top(){
        if(stackPos == 0){
            return null;
        }
        return (T) stackArray[stackPos - 1];
    }
    
    public void reverseStack(){
    	for(int i = 0; i < stackPos / 2; i++)
    	{
    	    Object temp = stackArray[i];
    	    stackArray[i] = stackArray[stackPos - i - 1];
    	    stackArray[stackPos - i - 1] = temp;
    	}
    }
    
    public void resetStack(){
        stackPos = 0;
    }
    public int size(){
        return stackPos;
    }
    
	public Object[] getStackArray(){
        return stackArray;
    };
    public int getStackPos(){
        return stackPos;
    }
    public void setStackPos(int stackPos){
        this.stackPos = stackPos;
    }

    public void copyStateFrom(Stack<T> stack){
        if(stack.stackSize > stackSize){
            this.stackSize = stack.stackSize;
            stackArray = new Object[this.stackSize];
        }
        this.stackPos = stack.stackPos;
        for(int i = 0; i < this.stackPos; i++){
            this.stackArray[i] = stack.stackArray[i];
        }
    }

}
