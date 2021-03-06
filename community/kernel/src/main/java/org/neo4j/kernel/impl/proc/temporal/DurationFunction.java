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
package org.neo4j.kernel.impl.proc.temporal;

import java.time.temporal.ChronoUnit;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalUnit;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.neo4j.kernel.api.exceptions.ProcedureException;
import org.neo4j.kernel.api.exceptions.Status;
import org.neo4j.kernel.api.proc.CallableUserFunction;
import org.neo4j.kernel.api.proc.Context;
import org.neo4j.kernel.api.proc.FieldSignature;
import org.neo4j.kernel.api.proc.Neo4jTypes;
import org.neo4j.kernel.api.proc.QualifiedName;
import org.neo4j.kernel.api.proc.UserFunctionSignature;
import org.neo4j.kernel.impl.proc.Procedures;
import org.neo4j.procedure.Description;
import org.neo4j.values.AnyValue;
import org.neo4j.values.storable.DurationValue;
import org.neo4j.values.storable.TemporalValue;
import org.neo4j.values.storable.TextValue;
import org.neo4j.values.virtual.MapValue;

import static org.neo4j.kernel.api.proc.FieldSignature.inputField;

@Description( "Construct a Duration value." )
class DurationFunction implements CallableUserFunction
{
    private static final UserFunctionSignature DURATION =
            new UserFunctionSignature(
                    new QualifiedName( new String[0], "duration" ),
                    Collections.singletonList( inputField( "input", Neo4jTypes.NTAny ) ),
                    Neo4jTypes.NTDuration, Optional.empty(), new String[0],
                    Optional.of( DurationFunction.class.getAnnotation( Description.class ).value() ) );

    static void register( Procedures procedures ) throws ProcedureException
    {
        procedures.register( new DurationFunction() );
        procedures.register( new Between( "between" ) );
        procedures.register( new Between( "years" ) );
        procedures.register( new Between( "quarters" ) );
        procedures.register( new Between( "months" ) );
        procedures.register( new Between( "weeks" ) );
        procedures.register( new Between( "days" ) );
        procedures.register( new Between( "hours" ) );
        procedures.register( new Between( "minutes" ) );
        procedures.register( new Between( "seconds" ) );
    }

    @Override
    public UserFunctionSignature signature()
    {
        return DURATION;
    }

    @Override
    public DurationValue apply( Context ctx, AnyValue[] input ) throws ProcedureException
    {
        if ( input != null && input.length == 1 )
        {
            if ( input[0] instanceof TextValue )
            {
                return DurationValue.parse( (TextValue) input[0] );
            }
            if ( input[0] instanceof MapValue )
            {
                MapValue map = (MapValue) input[0];
                return DurationValue.build( map );
            }
        }
        throw new ProcedureException( Status.Procedure.ProcedureCallFailed, "Invalid call signature" );
    }

    private static class Between implements CallableUserFunction
    {
        private static final String DESCRIPTION =
                "Compute the duration between the 'form' instant (inclusive) and the 'to' instant (exclusive) in %s.";
        private static final List<FieldSignature> SIGNATURE = Arrays.asList(
                inputField( "from", Neo4jTypes.NTAny ),
                inputField( "to", Neo4jTypes.NTAny ) );
        private final UserFunctionSignature signature;
        private final TemporalUnit unit;

        private Between( String unit )
        {
            this.signature = new UserFunctionSignature(
                    new QualifiedName( new String[] {"duration"}, unit ),
                    SIGNATURE, Neo4jTypes.NTDuration, Optional.empty(), new String[0],
                    Optional.of( String.format(
                            DESCRIPTION, "between".equals( unit ) ? "logical units" : unit ) ) );
            switch ( unit )
            {
            case "between":
                this.unit = null;
                break;
            case "years":
                this.unit = ChronoUnit.YEARS;
                break;
            case "quarters":
                this.unit = IsoFields.QUARTER_YEARS;
                break;
            case "months":
                this.unit = ChronoUnit.MONTHS;
                break;
            case "weeks":
                this.unit = ChronoUnit.WEEKS;
                break;
            case "days":
                this.unit = ChronoUnit.DAYS;
                break;
            case "hours":
                this.unit = ChronoUnit.HOURS;
                break;
            case "minutes":
                this.unit = ChronoUnit.MINUTES;
                break;
            case "seconds":
                this.unit = ChronoUnit.SECONDS;
                break;
            default:
                throw new IllegalStateException( "Unsupported unit: " + unit );
            }
        }

        @Override
        public UserFunctionSignature signature()
        {
            return signature;
        }

        @Override
        public AnyValue apply( Context ctx, AnyValue[] input ) throws ProcedureException
        {
            if ( input != null && input.length == 2 )
            {
                if ( input[0] instanceof TemporalValue && input[1] instanceof TemporalValue )
                {
                    TemporalValue from = (TemporalValue) input[0];
                    TemporalValue to = (TemporalValue) input[1];
                    return DurationValue.between( unit, from, to );
                }
            }
            throw new ProcedureException( Status.Procedure.ProcedureCallFailed, "Invalid call signature" );
        }
    }
}
