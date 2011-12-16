/**
 * Copyright (C) 2011 Akiban Technologies Inc.
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License, version 3,
 * as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see http://www.gnu.org/licenses.
 */

package com.akiban.server.expression.std;

import com.akiban.server.error.InconvertibleTypesException;
import com.akiban.server.error.WrongExpressionArityException;
import com.akiban.server.expression.Expression;
import com.akiban.server.expression.ExpressionComposer;
import com.akiban.server.expression.ExpressionEvaluation;
import com.akiban.server.expression.ExpressionType;
import com.akiban.server.service.functions.Scalar;
import com.akiban.server.types.AkType;
import com.akiban.server.types.ValueSource;
import com.akiban.server.types.extract.Extractors;
import com.akiban.server.types.extract.LongExtractor;
import com.akiban.server.types.extract.ObjectExtractor;
import com.akiban.server.types.util.ValueHolder;
import java.math.BigInteger;
import java.util.List;
import org.slf4j.LoggerFactory;

public class BinaryBitExpression extends AbstractBinaryExpression
{
    public static enum BitOperator
    {
        BITWISE_AND
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).and(bIntExtractor.getObject(right));
            }
        },
        BITWISE_OR
        {
            @Override
            public BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).or(bIntExtractor.getObject(right));
            }
        },
        BITWISE_XOR
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).xor(bIntExtractor.getObject(right));
            }
        },
        LEFT_SHIFT
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).shiftLeft((int)lExtractor.getLong(right));
            }
        },
        RIGHT_SHIFT
        {
            @Override
            protected BigInteger exc (ValueSource left, ValueSource right)
            {
                return bIntExtractor.getObject(left).shiftRight((int)lExtractor.getLong(right));
            }
        };

        protected abstract BigInteger exc (ValueSource left,ValueSource right);
        private static ObjectExtractor<BigInteger> bIntExtractor = Extractors.getUBigIntExtractor();
        private static LongExtractor lExtractor = Extractors.getLongExtractor(AkType.LONG);
    }
    
    @Scalar("bitand")
    public static final ExpressionComposer B_AND_COMPOSER = new InternalComposer(BitOperator.BITWISE_AND);
    
    @Scalar("bitor")
    public static final ExpressionComposer B_OR_COMPOSER = new InternalComposer(BitOperator.BITWISE_OR);
    
    @Scalar("bitxor")
    public static final ExpressionComposer B_XOR_COMPOSER = new InternalComposer(BitOperator.BITWISE_XOR);
    
    @Scalar("leftshift")
    public static final ExpressionComposer LEFT_SHIFT_COMPOSER = new InternalComposer(BitOperator.LEFT_SHIFT);
    
    @Scalar("rightshift")
    public static final ExpressionComposer RIGHT_SHIFT_COMPOSER = new InternalComposer(BitOperator.RIGHT_SHIFT);
        
    private final BitOperator op;
       
    protected static class InternalComposer extends BinaryComposer
    {
        protected final BitOperator op;
        
        public InternalComposer (BitOperator op)
        {
            this.op = op;
        }      

        @Override
        protected Expression compose(Expression first, Expression second) 
        {
            return new BinaryBitExpression(first, op,second);
        }

        @Override
        protected ExpressionType composeType(ExpressionType first, ExpressionType second)
        {
            return ExpressionTypes.U_BIGINT;
        }

        @Override
        public void argumentTypes(List<AkType> argumentTypes)
        {
            if (argumentTypes.size() != 2) 
                throw new WrongExpressionArityException(2, argumentTypes.size());
            argumentTypes.set(0, AkType.U_BIGINT);
            argumentTypes.set(1, op.ordinal() >= BitOperator.LEFT_SHIFT.ordinal() ? AkType.LONG : AkType.U_BIGINT );
        }
    }

    protected static class InnerEvaluation extends AbstractTwoArgExpressionEvaluation
    {
        private final BitOperator op;                
        private static final BigInteger n64 = new BigInteger("FFFFFFFFFFFFFFFF", 16);
        public InnerEvaluation (List<? extends ExpressionEvaluation> children, BitOperator op)
        {
            super(children);
            this.op = op;
        }
 
        @Override
        public ValueSource eval() 
        {
            BigInteger rst = BigInteger.ZERO;
            try
            {
                rst = op.exc(left(), right());
            }
            catch (InconvertibleTypesException ex) // acceptable error where the result will simply be 0
            {
                // if invalid types are supplied, 0 is assumed to be input
               LoggerFactory.getLogger(BinaryBitExpression.class).debug(ex.getShortMessage() + " - assume 0 as input");
            }   
            catch (NumberFormatException exc ) // acceptable error where the result will simply be 0
            {
                LoggerFactory.getLogger(BinaryBitExpression.class).debug(exc.getMessage() + " - assume 0 as input"); 
            }
            valueHolder().putUBigInt(rst.and(n64));
            return valueHolder();
        }
    }
    
    public BinaryBitExpression (Expression lhs, BitOperator op, Expression rhs)
    {
        super(lhs.valueType() == AkType.NULL || rhs.valueType() == AkType.NULL ? AkType.NULL : AkType.U_BIGINT
                , lhs, rhs);
        this.op = op;        
    } 

    @Override
    protected void describe(StringBuilder sb) 
    {
        sb.append(op);
    }

    @Override
    public ExpressionEvaluation evaluation() 
    {  
        if (valueType() == AkType.NULL ) return LiteralExpression.forNull().evaluation();
        return new InnerEvaluation(childrenEvaluations(), op);
    }    
        
    @Override
    public boolean nullIsContaminating ()
    {
        return true;
    }
}