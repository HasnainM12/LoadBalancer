module com.mycompany.javafxapplication1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires transitive java.sql;
    requires transitive javafx.graphics;
    requires javafx.base;
    requires java.logging;
    requires java.desktop;
    requires transitive org.eclipse.paho.client.mqttv3;
    requires jsch; 
    requires  transitive org.json;
    

    opens com.mycompany.javafxapplication1 to javafx.fxml, javafx.graphics, javafx.base;
    exports com.mycompany.javafxapplication1;
}
