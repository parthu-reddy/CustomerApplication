import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DBCheck {
    public static void main(String[] args) {
        try {
            Connection conn = DriverManager.getConnection("jdbc:oracle:thin:@140.245.234.137:1521/XEPDB1", "food_delivery", "food_delivery");
            Statement stmt = conn.createStatement();
            
            System.out.println("--- Customer Home Addresses ---");
            ResultSet rs = stmt.executeQuery("SELECT c.phone, a.latitude, a.longitude FROM customer_addresses a JOIN customers c ON a.customer_id = c.id WHERE a.label = 'Home' AND c.phone = '8000000001'");
            while (rs.next()) {
                System.out.println("Customer: " + rs.getString("phone") + ", Lat: " + rs.getDouble("latitude") + ", Lng: " + rs.getDouble("longitude"));
            }
            rs.close();
            
            System.out.println("--- Brand 1 Outlets ---");
            ResultSet rs2 = stmt.executeQuery("SELECT r.name, r.latitude, r.longitude FROM restaurants r WHERE r.name LIKE 'Brand 1%'");
            while (rs2.next()) {
                System.out.println("Outlet: " + rs2.getString("name") + ", Lat: " + rs2.getDouble("latitude") + ", Lng: " + rs2.getDouble("longitude"));
            }
            rs2.close();
            
            stmt.close();
            conn.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
