module LodInfra {
    requires com.google.gson;
    requires com.jfoenix;
    requires org.controlsfx.controls;
    requires org.apache.commons.text;
    requires jdk.localedata;
    requires java.prefs;
    requires pngj;

    opens com.github.artyomcool.lodinfra;
    opens com.github.artyomcool.lodinfra.data;
    opens com.github.artyomcool.lodinfra.ui;
    opens com.github.artyomcool.lodinfra.data.dto;
}