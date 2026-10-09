package com.local.smsmail;
import org.junit.Test;
import static org.junit.Assert.*;

public final class LogicTest {
    @Test public void acceptsQqAndOtherRecipient(){assertTrue(Logic.email("123456@qq.com"));assertTrue(Logic.email("a+b@example.org"));}
    @Test public void rejectsHeaderInjectionAndMultipleRecipients(){assertFalse(Logic.email("a@qq.com\r\nBcc:x@example.org"));assertFalse(Logic.email("a@qq.com,b@example.org"));assertFalse(Logic.email("a..b@qq.com"));assertFalse(Logic.email("a@qq.com\n"));}
    @Test public void duplicateSmsUsesStableKey(){assertEquals(Logic.id("1068",123,"你好\n长短信"),Logic.id("1068",123,"你好\n长短信"));}
    @Test public void distinctSmsMustRemainDistinct(){assertNotEquals(Logic.id("1068",123,"同一正文"),Logic.id("1068",124,"同一正文"));assertNotEquals(Logic.id("1068",123,"a"),Logic.id("1069",123,"a"));assertNotEquals(Logic.id("1068",123,"a"),Logic.id("1068",123,"b"));}
    @Test public void emptyFiltersForwardEverything(){assertTrue(Logic.matches("+8613812345678","正文", "", ""));}
    @Test public void senderRulesAreExactAndAcceptChineseComma(){assertTrue(Logic.matches("1068","订单", " 1067，1068 ", "订单"));assertFalse(Logic.matches("1068123","订单", "1068", ""));}
    @Test public void bothConditionsMustMatch(){assertFalse(Logic.matches("1068","广告", "1068", "订单"));assertFalse(Logic.matches("1069","订单", "1068", "订单"));}
    @Test public void smtpHostRejectsSchemesWhitespaceAndShellSyntax(){assertTrue(Logic.host("smtp.qq.com"));assertFalse(Logic.host("ssl://smtp.qq.com"));assertFalse(Logic.host("smtp.qq.com\r\n"));assertFalse(Logic.host("$(host)"));}
}
