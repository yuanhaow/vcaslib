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

class LongStack {
    private int stackSize;
    private int stackPos = 0;
    private long[] stackArray;
    
    public LongStack(int stackSize){
        this.stackSize = stackSize;
        stackArray = new long[stackSize];
    }

    public LongStack(){
        this(32);
    }
    public void push(long node){
        if(stackPos == stackSize){
            int newStackSize = stackSize*4;
        	long[] newStackArray = new long[newStackSize];
            for(int i = 0; i < stackSize;i++){
                newStackArray[i] = stackArray[i];
            }
            stackSize = newStackSize;
            stackArray = newStackArray;
        }
        stackArray[stackPos] = node;
        stackPos = stackPos + 1;
    }

	public long pop(){
        if(stackPos == 0){
            throw new RuntimeException("Attempt to pop empty long stack");
        }
        stackPos = stackPos - 1;
        return stackArray[stackPos];
    }

	public long top(){
        if(stackPos == 0){
        	throw new RuntimeException("Attempt to look at top of empty long stack");
        }
        return stackArray[stackPos - 1];
    }
    public void resetStack(){
        stackPos = 0;
    }
    public int size(){
        return stackPos;
    }
    
	public long[] getStackArray(){
        return stackArray;
    };
    public int getStackPos(){
        return stackPos;
    }
    public void setStackPos(int stackPos){
        this.stackPos = stackPos;
    }

    public void copyStateFrom(LongStack stack){
        if(stack.stackSize > stackSize){
            this.stackSize = stack.stackSize;
            stackArray = new long[this.stackSize];
        }
        this.stackPos = stack.stackPos;
        for(int i = 0; i < this.stackPos; i++){
            this.stackArray[i] = stack.stackArray[i];
        }
    }

}
