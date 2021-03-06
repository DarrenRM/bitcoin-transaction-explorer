package com.yoghurt.crypto.transactions.client.widget;

import com.google.gwt.dom.client.Document;
import com.google.gwt.event.dom.client.ClickEvent;
import com.google.gwt.event.dom.client.ClickHandler;
import com.google.gwt.event.dom.client.HasClickHandlers;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.gwt.uibinder.client.UiConstructor;
import com.google.gwt.user.client.ui.HasHTML;
import com.google.gwt.user.client.ui.HasText;
import com.google.gwt.user.client.ui.Widget;

public class HeadingWidget extends Widget implements HasText, HasHTML, HasClickHandlers {
  @UiConstructor
  public HeadingWidget(final int n) {
    setElement(Document.get().createHElement(n));
  }

  public HeadingWidget(final int n, final String key) {
    this(n);

    setText(key);
  }

  @Override
  public String getText() {
    return getElement().getInnerText();
  }

  @Override
  public void setText(final String text) {
    getElement().setInnerText(text);
  }

  @Override
  public String getHTML() {
    return getElement().getInnerHTML();
  }

  @Override
  public void setHTML(final String html) {
    getElement().setInnerHTML(html);
  }

  @Override
  public HandlerRegistration addClickHandler(final ClickHandler handler) {
    return addDomHandler(handler, ClickEvent.getType());
  }
}