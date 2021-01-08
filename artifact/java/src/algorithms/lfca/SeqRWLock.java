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

import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

import sun.misc.Unsafe;

public class SeqRWLock {
	
    volatile long seqNumber = 2L;
    volatile long readIndicator = 0L;
    volatile long writeBarrier = 0L;
    private int statLockStatistics = 0;
    
    private static final AtomicLongFieldUpdater<SeqRWLock> seqNumberUpdater =
            AtomicLongFieldUpdater.newUpdater(SeqRWLock.class, "seqNumber");
    
    private static final AtomicLongFieldUpdater<SeqRWLock> writeBarrierUpdater =
            AtomicLongFieldUpdater.newUpdater(SeqRWLock.class, "writeBarrier");
    
    private static final AtomicLongFieldUpdater<SeqRWLock> readIndicatorUpdater =
            AtomicLongFieldUpdater.newUpdater(SeqRWLock.class, "readIndicator");
    
    private static final Unsafe unsafe;

    private static final int STAT_LOCK_HIGH_CONTENTION_LIMIT = 1000;
    private static final int STAT_LOCK_LOW_CONTENTION_LIMIT = -1000;
    private static final int STAT_LOCK_FAILURE_CONTRIB = 250;
    private static final int STAT_LOCK_SUCCESS_CONTRIB = 1;
    
    static {
        try {
            Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafe = (Unsafe) theUnsafe.get(null);
        } catch (Exception ex) { 
            throw new Error(ex);
        }
    }
    
    public void indicateReadStart(){
    	readIndicatorUpdater.getAndIncrement(this);
    }

    public void indicateReadEnd(){
    	readIndicatorUpdater.getAndDecrement(this);
    }
    
    private void waitNoOngoingRead(){
    	while(0L != readIndicator){
    		unsafe.fullFence();
    		unsafe.fullFence();
         }
    }
    
    
	public boolean tryLock() {
    	long readSeqNumber = seqNumber;
		if(writeBarrier != 0 || readIndicator != 0 || (readSeqNumber % 2) != 0){
			return false;
		}else{
			boolean success = seqNumberUpdater.compareAndSet(this, readSeqNumber, readSeqNumber + 1);
			if(success){
			    if(readIndicator != 0){
				seqNumber = readSeqNumber + 2;
				return false;
			    }else{
				return true;
			    }
			}else{
				return false;
			}
		}
	}
    
    public void lock(){
    	while(writeBarrier != 0L){
    		Thread.yield();
    	}
    	while(true){
        	long readSeqNumber = seqNumber;	
        	while((readSeqNumber % 2) != 0){
        		unsafe.fullFence();
        		unsafe.fullFence();
        		readSeqNumber = seqNumber;
        	}
        	if(seqNumberUpdater.compareAndSet(this, readSeqNumber, readSeqNumber + 1)){
        		break;
        	}
    	}
    	waitNoOngoingRead();
    }

    public void unlock(){
    	seqNumber = seqNumber + 1;
    }
    
    public boolean isWriteLocked(){
    	return (seqNumber % 2) != 0;
    }
    
    public void readLock(){
    	boolean barrierRaised = false;
    	int patience = 10000;
    	while(true){
    		indicateReadStart();
    		if(isWriteLocked()){
    			indicateReadEnd();
    			while(isWriteLocked()){
    				//Thread.yield();
            		unsafe.fullFence();
            		unsafe.fullFence();
    				if(patience == 0 && !barrierRaised){
    					writeBarrierUpdater.incrementAndGet(this);
    					barrierRaised = true;
    				}
    				patience--;
    			}
    		}else{
    			break;
    		}
    	}
		if(barrierRaised){
			writeBarrierUpdater.decrementAndGet(this);
		}
    }

    public void readUnlock(){
    	indicateReadEnd();    	
    }

	public long tryOptimisticRead() {
		long readSeqNumber = seqNumber;
		if((readSeqNumber % 2) != 0){
			return 0;
		}else{
			return readSeqNumber;
		}
	}

	public boolean validate(long optimisticReadToken) {
		unsafe.loadFence();
		long readSeqNumber = seqNumber;
		return readSeqNumber == optimisticReadToken;
	}
	
	
    public void lockUpdateStatistics(){
        if (tryLock()) {
            statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
            return;
        }
        lock();
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void addToContentionStatistics(){
        statLockStatistics += STAT_LOCK_FAILURE_CONTRIB;
    }

    public void subFromContentionStatistics(){
        statLockStatistics -= STAT_LOCK_SUCCESS_CONTRIB;
    }

    public void subManyFromContentionStatistics(){
        statLockStatistics -= 100;
    }

    
    public int getLockStatistics(){
    	return statLockStatistics;
    }
    
    public void resetStatistics(){
        statLockStatistics = 0;
    }

    public boolean isHighContentionLimitReached(){
        return statLockStatistics > STAT_LOCK_HIGH_CONTENTION_LIMIT;
    }
    
    public boolean isLowContentionLimitReached(){
        return statLockStatistics < STAT_LOCK_LOW_CONTENTION_LIMIT;
    }
    
}
