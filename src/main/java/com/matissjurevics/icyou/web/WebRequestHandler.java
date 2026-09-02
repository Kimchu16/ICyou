package com.matissjurevics.icyou.web;

@FunctionalInterface
public interface WebRequestHandler {
    WebResponse handle(WebRequest request);
}
