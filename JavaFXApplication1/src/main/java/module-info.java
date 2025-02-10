module com.mycompany.javafxapplication1 {
    requires javafx.controls;
    requires javafx.fxml;
    requires java.base;
    requires transitive java.sql;
    requires transitive javafx.graphics;
    requires javafx.base;

    opens com.mycompany.javafxapplication1 to javafx.fxml, javafx.graphics, javafx.base;
    exports com.mycompany.javafxapplication1;
}
