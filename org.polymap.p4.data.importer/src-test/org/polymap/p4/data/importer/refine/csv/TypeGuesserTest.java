package org.polymap.p4.data.importer.refine.csv;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.io.UnsupportedEncodingException;
import java.text.ParseException;

import org.junit.Test;

public class TypeGuesserTest {

    @Test
    public void testText() {
        assertNull( TypeGuesser.guess( null ) );
        assertNull( TypeGuesser.guess( "" ) );
        assertNull( TypeGuesser.guess( "  " ) );
        assertEquals( Type.Text, TypeGuesser.guess( "04683" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "46ab83" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "abe 83" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "e 83" ).type() );
        assertEquals( Type.Decimal, TypeGuesser.guess( "83e34" ).type() );
        assertEquals( Type.Decimal, TypeGuesser.guess( "83" ).type() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83,56" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83,56" ).locale().getLanguage()  );

        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456" ).type() );
        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456.456" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83.456.456" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83 456 456" ).type() );
        assertEquals( "ru", TypeGuesser.guess( "83 456 456" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456,45" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83.456,45" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456,45e12" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83.456,45e12" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456,45e-12" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83.456,45e-12" ).locale().getLanguage() );

        assertEquals( Type.Decimal, TypeGuesser.guess( "83.456.456,45" ).type() );
        assertEquals( "de", TypeGuesser.guess( "83.456.456,45" ).locale().getLanguage() );

        assertEquals( Type.Decimal, TypeGuesser.guess( "83 456 456,45" ).type() );
        assertEquals( "ru", TypeGuesser.guess( "83 456 456,45" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "83,456,456.45" ).type() );
        assertEquals( "en", TypeGuesser.guess( "83,456,456.45" ).locale().getLanguage() );
        
        assertEquals( Type.Decimal, TypeGuesser.guess( "-83.456.456,45" ).type() );
        assertEquals( Type.Decimal, TypeGuesser.guess( "83,456,456.45-" ).type() );
        assertEquals( "ar", TypeGuesser.guess( "83,456,456.45-" ).locale().getLanguage() );
        
        assertEquals( Type.Text, TypeGuesser.guess( "83.456,45e-1-2" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "83,456,45" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "-83.456.46,45" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "83,456,46.45-" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "16.5.1975" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "20:40" ).type() );
        assertEquals( Type.Text, TypeGuesser.guess( "20:40:455" ).type() );
    }


    public static void main( String[] args ) throws ParseException, UnsupportedEncodingException {
//        NumberFormat nf = NumberFormat.getInstance();
//        // nf.setMaximumIntegerDigits( 3 );
//        System.out.println( nf.parse( "123E3" ) );
//
//        ListMultimap<String, Locale> formats = ArrayListMultimap.create();
//        for (Locale locale : DecimalFormat.getAvailableLocales()) {
//            if (locale.getCountry().length() != 0) {
//                continue; // Skip sub-language locales
//            }
//            formats.put( ((DecimalFormat)NumberFormat.getInstance( locale )).toLocalizedPattern(), locale );
//        }
//        formats.asMap().forEach( (key, value) -> System.out.println(key + ": " + value) );
//        long start = System.currentTimeMillis();
//        int count = 0;
//        for (int i = 0; i< (1000000); i++) {
//            TypeGuesser.guess( "83,456,456.45-" );
//            count++;
//        }
//        System.out.println( "Needed " + (System.currentTimeMillis() - start) + "ms for " + count );
        
//        String s1 = new String( "a�" );
//        for (byte b : s1.getBytes()) {
//            System.out.println( UnicodeEscaper.hex( b ));
//        }
//        Pattern asciiOnly = Pattern.compile("\\p{ASCII}*");
//        List<String> strings = Lists.newArrayList( "a�", "abc", "a.b.c", "abc\n", "�" );
//        strings.forEach( str ->  System.out.println( str + " matches? " + asciiOnly.matcher( str ).matches() ));
        
        
        String str = "���";
        byte[] b = str.getBytes("utf-8");
        
        String str2 = new String(b, "utf-8");
        System.out.println( str2 );
        
    }
}
