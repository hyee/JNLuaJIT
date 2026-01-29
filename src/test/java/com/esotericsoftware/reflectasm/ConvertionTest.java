package com.esotericsoftware.reflectasm;

import org.junit.Test;

import java.util.Arrays;

import static com.esotericsoftware.reflectasm.util.NumberUtils.convert;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Created by Will on 2017/2/10.
 */
public class ConvertionTest {
    @Test
    public void testConvert() {
        assertTrue(Integer.class == convert(1L, int.class).getClass());
        assertEquals(Integer[].class, convert(new String[]{"1", "2", "3"}, Integer[].class).getClass());
        assertEquals("[1, 2]", Arrays.toString(convert(new Object[]{Double.valueOf(1.6), Float.valueOf(2.3F)}, Integer[].class)));
        assertEquals(Character.class, convert(123, char.class).getClass());
        int i = 0;
    }

    public abstract class Abstract<T extends Abstract<T>> {
        private final Class<? extends T> subClass;

        protected Abstract(Class<? extends T> subClass) {
            this.subClass = subClass;
        }
    }
}
