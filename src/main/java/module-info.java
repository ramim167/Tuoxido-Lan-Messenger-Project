module com.lanmessenger {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.materialdesign2;
    requires java.net.http;
    requires com.google.gson;
    requires java.prefs;
    requires org.mongodb.driver.sync.client;
    requires org.mongodb.driver.core;
    requires org.mongodb.bson;
    requires webcam.capture;
    requires java.desktop;

    opens com.lanmessenger to javafx.fxml;
    exports com.lanmessenger;
}
