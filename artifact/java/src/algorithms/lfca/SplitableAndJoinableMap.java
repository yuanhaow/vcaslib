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

import java.util.Map;

public interface SplitableAndJoinableMap<K, V> extends Map<K,V>{
    public SplitableAndJoinableMap<K, V> join(SplitableAndJoinableMap<K, V> right);
    public SplitableAndJoinableMap<K, V> split(Object[] splitKeyWriteBack,
                                               SplitableAndJoinableMap<K, V>[] rightTreeWriteBack);
}
