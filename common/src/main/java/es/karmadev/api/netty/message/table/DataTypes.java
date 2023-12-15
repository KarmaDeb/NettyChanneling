package es.karmadev.api.netty.message.table;

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

import lombok.Getter;

@Getter
public enum DataTypes {
    BYTE((byte) 0),
    UTF((byte) 1),
    INT16((byte) 2),
    INT32((byte) 3),
    INT64((byte) 4),
    FLOAT32((byte) 5),
    FLOAT64((byte) 6),
    BOOLEAN((byte) 7),
    JSON((byte) 8);

    private final byte id;

    DataTypes(final byte id) {
        this.id = id;
    }

    public static DataTypes byId(final byte id) {
        for (DataTypes type : DataTypes.values()) {
            if (type.id == id) return type;
        }

        return null;
    }
}
