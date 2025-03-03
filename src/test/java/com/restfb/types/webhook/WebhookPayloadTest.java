/*
 * Copyright (c) 2010-2025 Mark Allen, Norbert Bartels.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.restfb.types.webhook;

import com.restfb.types.webhook.whatsapp.AbstractWhatsappBaseChangeValue;
import com.restfb.types.webhook.whatsapp.WhatsappMessagesValue;
import com.restfb.types.whatsapp.platform.status.CategoryType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.restfb.DefaultJsonMapper;
import com.restfb.JsonMapper;

public class WebhookPayloadTest {
	
	@Test
	public void testPayload() {
		String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"9999999999\",\"changes\":[{\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"1111111\",\"phone_number_id\":\"222222222\"},\"statuses\":[{\"id\":\"wamid.bla\",\"status\":\"delivered\",\"timestamp\":\"1685965154\",\"recipient_id\":\"999999999\",\"conversation\":{\"id\":\"698ed3cebasdas23asd44baa7c5387fd9013\",\"origin\":{\"type\":\"marketing\"}},\"pricing\":{\"billable\":true,\"pricing_model\":\"CBP\",\"category\":\"marketing\"}}]},\"field\":\"messages\"}]}]}";
		
		JsonMapper mapper = new DefaultJsonMapper();
		WebhookObject whObject = mapper.toJavaObject(payload, WebhookObject.class);
	}
	
	@Test
	public void testPayload2() {
		String payload = "{\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"1111111\",\"phone_number_id\":\"222222222\"},\"contacts\":[{\"profile\":{\"name\":\"johndoe\"},\"wa_id\":\"3333333333\"}],\"messages\":[{\"from\":\"4444444444\",\"id\":\"wamid.bla\",\"timestamp\":\"1721687120\",\"errors\":[{\"code\":131051,\"title\":\"Message type unknown\",\"message\":\"Message type unknown\",\"error_data\":{\"details\":\"Message type is currently not supported.\"}}],\"type\":\"unsupported\"}]},\"field\":\"messages\"}";

		JsonMapper mapper = new DefaultJsonMapper();
		WebhookObject whObject = mapper.toJavaObject(payload, WebhookObject.class);
	}

	@Test
	public void testPayloadAuthenticationInternational() {
		String payload = "{\"object\":\"whatsapp_business_account\",\"entry\":[{\"id\":\"9999999999\",\"changes\":[{\"value\":{\"messaging_product\":\"whatsapp\",\"metadata\":{\"display_phone_number\":\"1111111\",\"phone_number_id\":\"222222222\"},\"statuses\":[{\"id\":\"wamid.bla\",\"status\":\"delivered\",\"timestamp\":\"1685965154\",\"recipient_id\":\"999999999\",\"conversation\":{\"id\":\"698ed3cebasdas23asd44baa7c5387fd9013\",\"origin\":{\"type\":\"marketing\"}},\"pricing\":{\"billable\":true,\"pricing_model\":\"CBP\",\"category\":\"authentication-international\"}}]},\"field\":\"messages\"}]}]}";

		JsonMapper mapper = new DefaultJsonMapper();
		WebhookObject whObject = mapper.toJavaObject(payload, WebhookObject.class);
		whObject.getEntryList().forEach(entry -> {
			entry.getChanges().forEach(change -> {
				Assertions.assertTrue(change.getValue().isWhatsapp());
				AbstractWhatsappBaseChangeValue value = change.getValue().convertChangeValue(AbstractWhatsappBaseChangeValue.class);
				Assertions.assertInstanceOf(WhatsappMessagesValue.class, value);
				((WhatsappMessagesValue) value).getStatuses().forEach(status -> {
					Assertions.assertEquals(CategoryType.authentication_international, status.getPricing().getCategory());
				});
			});
		});
	}

}
