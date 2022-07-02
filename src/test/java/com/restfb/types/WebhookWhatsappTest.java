/*
 * Copyright (c) 2010-2022 Mark Allen, Norbert Bartels.
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
package com.restfb.types;

import static com.restfb.testutils.RestfbAssertions.assertThat;

import java.util.Date;

import org.junit.jupiter.api.Test;

import com.restfb.AbstractJsonMapperTests;
import com.restfb.types.webhook.ChangeValue;
import com.restfb.types.webhook.WebhookEntry;
import com.restfb.types.webhook.WebhookObject;
import com.restfb.types.webhook.whatsapp.*;
import com.restfb.types.whatsapp.platform.Contact;
import com.restfb.types.whatsapp.platform.Message;
import com.restfb.types.whatsapp.platform.message.Image;
import com.restfb.types.whatsapp.platform.message.MessageType;

class WebhookWhatsappTest extends AbstractJsonMapperTests {

  @Test
  void messageTemplateStatusUpdate_approved() {
    MessageTemplateStatusUpdateValue change = getWHObjectFromJson("webhook-messageTemplateStatusUpdate-approved", MessageTemplateStatusUpdateValue.class);
    assertThat(change.getMessageTemplateId()).isEqualTo("1234567");
    assertThat(change.getEvent()).isEqualTo("APPROVED");
    assertThat(change.getMessageTemplateName()).isEqualTo("My message template");
    assertThat(change.getMessageTemplateLanguage()).isEqualTo("en-US");
    assertThat(change.getMessageTemplateId()).isEqualTo("1234567");
    assertThat(change.getDisableInfo()).isNull();
  }

  @Test
  void messageTemplateStatusUpdate_disabling() {
    MessageTemplateStatusUpdateValue change = getWHObjectFromJson("webhook-messageTemplateStatusUpdate-disabling", MessageTemplateStatusUpdateValue.class);
    assertThat(change.getMessageTemplateId()).isEqualTo("1234567");
    assertThat(change.getEvent()).isEqualTo("FLAGGED");
    assertThat(change.getMessageTemplateName()).isEqualTo("My message template");
    assertThat(change.getMessageTemplateLanguage()).isEqualTo("en-US");
    assertThat(change.getMessageTemplateId()).isEqualTo("1234567");
    assertThat(change.getDisableInfo()).isNotNull();
  }

  @Test
  void phoneNumberNameUpdate() {
    PhoneNumberNameUpdateValue change = getWHObjectFromJson("webhook-phoneNumberNameUpdate", PhoneNumberNameUpdateValue.class);
    assertThat(change.getDisplayPhoneNumber()).isEqualTo("16505551111");
    assertThat(change.getDecision()).isEqualTo("APPROVED");
    assertThat(change.getRequestedVerifiedName()).isEqualTo("WhatsApp");
    assertThat(change.getRejectionReason()).isNull();
  }

  @Test
  void phoneNumberQualityUpdate() {
    PhoneNumberQualityUpdateValue change = getWHObjectFromJson("webhook-phoneNumberQualityUpdate", PhoneNumberQualityUpdateValue.class);
    assertThat(change.getDisplayPhoneNumber()).isEqualTo("16505551111");
    assertThat(change.getCurrentLimit()).isEqualTo("TIER_10K");
    assertThat(change.getEvent()).isEqualTo("FLAGGED");
  }

  @Test
  void accountUpdateVerified() {
    AccountUpdateValue change = getWHObjectFromJson("webhook-accountUpdate-verified", AccountUpdateValue.class);
    assertThat(change.getPhoneNumber()).isEqualTo("16505551111");
    assertThat(change.getEvent()).isEqualTo("VERIFIED_ACCOUNT");
    assertThat(change.getBanInfo()).isNull();
  }

  @Test
  void accountUpdateBanned() {
    AccountUpdateValue change = getWHObjectFromJson("webhook-accountUpdate-banned", AccountUpdateValue.class);
    assertThat(change.getPhoneNumber()).isNull();
    assertThat(change.getEvent()).isEqualTo("DISABLED_UPDATE");
    assertThat(change.getBanInfo()).isNotNull();
    assertThat(change.getBanInfo().getWabaBanDate()).isEqualTo("January 31, 2021");
    assertThat(change.getBanInfo().getWabaBanState()).isEqualTo("SCHEDULE_FOR_DISABLE");
  }

  @Test
  void accountReviewUpdate() {
    AccountReviewUpdateValue change = getWHObjectFromJson("webhook-accountReviewUpdate", AccountReviewUpdateValue.class);
    assertThat(change.getDecision()).isEqualTo("APPROVED");
  }

  @Test
  void incomingMessageText() {
    WhatsappMessagesValue change = getWHObjectFromJson("webhook-incoming-message-text", WhatsappMessagesValue.class);
    assertThat(change).isInstanceOf(WhatsappMessagesValue.class);

    // check contact
    assertThat(change.getContacts()).hasSize(1);
    Contact contact = change.getContacts().get(0);
    assertThat(contact.getWaId()).isEqualTo("491234567890");
    assertThat(contact.getProfile()).isNotNull();
    assertThat(contact.getProfile().getName()).isEqualTo("TestUser");

    // check Metadata
    assertThat(change.getMetadata()).isNotNull();
    assertThat(change.getMetadata().getDisplayPhoneNumber()).isEqualTo("1234567891");
    assertThat(change.getMetadata().getPhoneNumberId()).isEqualTo("10634295353625");

    // check Message
    assertThat(change.getMessages()).hasSize(1);
    Message message = change.getMessages().get(0);
    assertThat(message.getText()).isNotNull();
    assertThat(message.getText().getBody()).isEqualTo("Test");
    assertThat(message.getTimestamp()).isEqualTo(new Date(1653253313000L));
    assertThat(message.getType()).isEqualTo(MessageType.text);
    assertThat(message.getFrom()).isEqualTo("491234567890");
    assertThat(message.isText()).isTrue();
  }

  @Test
  void incomingMessageImage() {
    WhatsappMessagesValue change = getWHObjectFromJson("webhook-incoming-message-image", WhatsappMessagesValue.class);
    assertThat(change).isInstanceOf(WhatsappMessagesValue.class);

    // check contact
    assertThat(change.getContacts()).hasSize(1);
    Contact contact = change.getContacts().get(0);
    assertThat(contact.getWaId()).isEqualTo("491234567890");
    assertThat(contact.getProfile()).isNotNull();
    assertThat(contact.getProfile().getName()).isEqualTo("TestUser");

    // check Metadata
    assertThat(change.getMetadata()).isNotNull();
    assertThat(change.getMetadata().getDisplayPhoneNumber()).isEqualTo("1234567891");
    assertThat(change.getMetadata().getPhoneNumberId()).isEqualTo("10634295353625");

    // check Message
    assertThat(change.getMessages()).hasSize(1);
    Message message = change.getMessages().get(0);
    assertThat(message.getImage()).isNotNull();
    assertThat(message.isImage()).isTrue();
    assertThat(message.isText()).isFalse();

    Image image = message.getImage();
    assertThat(image.getCaption()).isEqualTo("Some awesome image");
    assertThat(image.getId()).isEqualTo("400962571939895");
    assertThat(image.getMimeType()).isEqualTo("image\\/jpeg");
    assertThat(image.getSha256()).isEqualTo("law0CgE277wMMnIJU0XIxhDne6Ptmwaek\\/thVM7mVtg=");

    assertThat(message.getTimestamp()).isEqualTo(new Date(1653253313000L));
    assertThat(message.getType()).isEqualTo(MessageType.image);
    assertThat(message.getFrom()).isEqualTo("491234567890");
  }

  private <T extends ChangeValue> T getWHObjectFromJson(String jsonName, Class<T> clazz) {
    WebhookObject webhookObject =
            createJsonMapper().toJavaObject(jsonFromClasspath("whatsapp/" + jsonName), WebhookObject.class);
    assertThat(webhookObject.isWhatsAppBusinessAccount()).isTrue();
    assertThat(webhookObject.getEntryList()).hasSize(1);
    WebhookEntry entry = webhookObject.getEntryList().get(0);
    return entry.getChanges().get(0).getValue().convertChangeValue(clazz);
  }

}
