/*
 * Copyright (c) 2002-2018 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.bolt.v1.messaging;

import java.io.IOException;
import java.util.Map;

import org.neo4j.bolt.v1.packstream.PackStream;
import org.neo4j.bolt.v1.runtime.Neo4jError;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.values.virtual.MapValue;

import static java.util.stream.Collectors.toMap;

/**
 * Reader for Bolt request messages made available via a {@link Neo4jPack.Unpacker}.
 */
public class BoltRequestMessageReader
{
    private final Neo4jPack.Unpacker unpacker;

    public BoltRequestMessageReader( Neo4jPack.Unpacker unpacker )
    {
        this.unpacker = unpacker;
    }

    /**
     * Parse and handle a single message by handing it off
     * to a {@link BoltRequestMessageHandler} instance.
     *
     * @param handler handler for request messages
     */
    public <E extends Exception> void read( BoltRequestMessageHandler<E> handler ) throws IOException, E
    {
        try
        {
            unpacker.unpackStructHeader();
            final int signature = (int) unpacker.unpackStructSignature();
            BoltRequestMessage message = BoltRequestMessage.withSignature( signature );
            try
            {
                switch ( message )
                {
                case INIT:
                    String clientName = unpacker.unpackString();
                    Map<String,Object> authToken = readAuthToken( unpacker );
                    handler.onInit( clientName, authToken );
                    break;
                case ACK_FAILURE:
                    handler.onAckFailure();
                    break;
                case RESET:
                    handler.onReset();
                    break;
                case RUN:
                    String statement = unpacker.unpackString();
                    MapValue params = unpacker.unpackMap();
                    Neo4jError error = unpacker.consumeError();
                    if ( error != null )
                    {
                        handler.onExternalError( error );
                    }
                    else
                    {
                        handler.onRun( statement, params );
                    }
                    break;
                case DISCARD_ALL:
                    handler.onDiscardAll();
                    break;
                case PULL_ALL:
                    handler.onPullAll();
                    break;
                default:
                    throw new BoltIOException( Status.Request.Invalid,
                            "Message 0x" + Integer.toHexString( signature ) + " is not supported." );
                }
            }
            catch ( IllegalArgumentException e )
            {
                throw new BoltIOException( Status.Request.Invalid,
                        "0x" + Integer.toHexString( signature ) + " is not a valid message signature." );
            }
        }
        catch ( PackStream.PackStreamException e )
        {
            throw new BoltIOException( Status.Request.InvalidFormat, "Unable to read message type. " +
                                                                     "Error was: " + e.getMessage(), e );
        }
    }

    private static Map<String,Object> readAuthToken( Neo4jPack.Unpacker unpacker ) throws IOException
    {
        MapValue authTokenValue = unpacker.unpackMap();
        AuthTokenValuesWriter writer = new AuthTokenValuesWriter();
        return authTokenValue.entrySet()
                .stream()
                .collect( toMap( Map.Entry::getKey, entry -> writer.valueAsObject( entry.getValue() ) ) );
    }
}
