package es.karmadev.api.netty.message.table.entry;

/*
 * Copyright 2023 KarmaDev
 *
 * This file is part of NettyChanneling.
 *
 * NettyChanneling is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NettyChanneling is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NettyChanneling. If not, see <http://www.gnu.org/licenses/>.
 */

import es.karmadev.api.netty.message.table.DataTypes;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.nio.ByteBuffer;

@AllArgsConstructor @Getter
public class TableEntry {

    protected final DataTypes type;
    protected final int origin;
    protected final int destination;

    public byte[] wrap() {
        byte dataType = type.getId();
        byte[] originAlloc = ByteBuffer.allocate(4).putInt(origin).array();
        byte[] destAlloc = ByteBuffer.allocate(4).putInt(destination).array();

        byte[] block = new byte[9];
        block[0] = dataType;
        System.arraycopy(originAlloc, 0, block, 1, 4);
        System.arraycopy(destAlloc, 0, block, 5, 4);

        return block;
    }
}
