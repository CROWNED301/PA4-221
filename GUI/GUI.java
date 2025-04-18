import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.sql.*;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.awt.event.*;
import java.math.BigDecimal;

public class GUI extends JFrame {
    private JTabbedPane tabbedPane;

    public static class DatabaseConnection {
        private static final String DB_URL = "jdbc:mysql://localhost:3306/northwind";
        private static final String USER = "root";
        private static final String PASS = "password1";

        public static Connection getConnection() throws SQLException {
            try {
                Class.forName("com.mysql.cj.jdbc.Driver");
            } catch (ClassNotFoundException e) {
                throw new SQLException("MySQL JDBC Driver not found", e);
            }
            return DriverManager.getConnection(DB_URL, USER, PASS);
        }

        public static void executeSQLFile(String filePath) throws SQLException, IOException {
            String content = new String(Files.readAllBytes(Paths.get(filePath)));
            try (Connection conn = getConnection();
                 Statement stmt = conn.createStatement()) {
                
                String[] queries = content.split(";(?=(?:[^']*'[^']*')*[^']*$)");
                
                for (String query : queries) {
                    if (!query.trim().isEmpty()) {
                        try {
                            stmt.executeUpdate(query);
                        } catch (SQLException e) {
                            if (!query.trim().toUpperCase().startsWith("DROP")) {
                                throw e;
                            }
                        }
                    }
                }
            }
        }
    }

    public GUI() {
        setTitle("Northwind Database Manager");
        setSize(1000, 600);
        setDefaultCloseOperation(EXIT_ON_CLOSE);

        initializeDatabase();

        tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Employees", createEmployeeTab());
        tabbedPane.addTab("Products", createProductTab());
        tabbedPane.addTab("Customers", createCustomerTab());
        tabbedPane.addTab("Orders", createOrderTab());
        tabbedPane.addTab("Reports", createReportTab());
        tabbedPane.addTab("Notifications", createNotificationsTab());

        add(tabbedPane);
        setLocationRelativeTo(null);
        setVisible(true);
    }

    private void initializeDatabase() {
        try {
            DatabaseConnection.executeSQLFile("northwind-schema.sql");
            
            String dataSql = new String(Files.readAllBytes(Paths.get("northwind-data.sql")));
            String[] insertQueries = dataSql.split(";(?=(?:[^']*'[^']*')*[^']*$)");
            
            StringBuilder executedQueries = new StringBuilder();
            int totalInserts = 0;
            
            try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement()) {
                
                for (String query : insertQueries) {
                    query = query.trim();
                    if (query.toUpperCase().startsWith("INSERT")) {
                        try {
                            int rowsAffected = stmt.executeUpdate(query);
                            executedQueries.append("Executed: ").append(query)
                                        .append("\nRows affected: ").append(rowsAffected)
                                        .append("\n\n");
                            totalInserts++;
                        } catch (SQLException e) {
                            executedQueries.append("Failed: ").append(query)
                                        .append("\nError: ").append(e.getMessage())
                                        .append("\n\n");
                        }
                    }
                }
            }
            
            JTextArea textArea = new JTextArea(20, 80);
            textArea.setText("Database initialized successfully!\n\n" +
                            "Total INSERT operations: " + totalInserts + "\n\n" +
                            executedQueries.toString());
            textArea.setEditable(false);
            JScrollPane scrollPane = new JScrollPane(textArea);
            
            JOptionPane.showMessageDialog(this, scrollPane, 
                "Database Initialization Results", JOptionPane.INFORMATION_MESSAGE);
            
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error initializing database: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private JPanel createEmployeeTab() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] columnNames = {
            "ID", 
            "First Name", 
            "Last Name", 
            "Address", 
            "City", 
            "Region", 
            "Postal Code", 
            "Phone", 
            "Office", 
            "Active"
        };

        DefaultTableModel model = new DefaultTableModel(columnNames, 0) {
            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 9) return Boolean.class;
                return super.getColumnClass(columnIndex);
            }
        };

        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        JPanel filterPanel = new JPanel();
        JTextField filterField = new JTextField(20);
        JButton filterButton = new JButton("Filter");
        filterPanel.add(new JLabel("Filter by Name or City:"));
        filterPanel.add(filterField);
        filterPanel.add(filterButton);

        panel.add(filterPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        Runnable loadEmployees = () -> {
            model.setRowCount(0); 
            String filter = filterField.getText().trim();
            String query = "SELECT id, first_name, last_name, address, city, state_province AS region, " +
                        "zip_postal_code, business_phone AS phone, company AS office " +
                        "FROM employees";

            if (!filter.isEmpty()) {
                query += " WHERE first_name LIKE '%" + filter + "%' " +
                        "OR last_name LIKE '%" + filter + "%' " +
                        "OR city LIKE '%" + filter + "%'";
            }

            try (Connection conn = DatabaseConnection.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(query)) {

                while (rs.next()) {
                    model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getString("first_name"),
                        rs.getString("last_name"),
                        rs.getString("address"),
                        rs.getString("city"),
                        rs.getString("region"),
                        rs.getString("zip_postal_code"),
                        rs.getString("phone"),
                        rs.getString("office"),
                        true 
                    });
                }
            } catch (SQLException e) {
                JOptionPane.showMessageDialog(panel, "Error loading employees: " + e.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        filterButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                loadEmployees.run();
            }
        });

        loadEmployees.run();

        return panel;
    }

    private JPanel createProductTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columnNames = {"ID", "Product Name", "Category", "Standard Cost", "List Price"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        JButton addButton = new JButton("Add Product");

        // Load product data
        Runnable loadProducts = new Runnable() {
            public void run() {
                model.setRowCount(0);
                try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, product_name, category, standard_cost, list_price FROM products")) {
                    
                    while (rs.next()) {
                        model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("product_name"),
                            rs.getString("category"),
                            rs.getBigDecimal("standard_cost"),
                            rs.getBigDecimal("list_price")
                        });
                    }
                } catch (SQLException e) {
                    JOptionPane.showMessageDialog(panel, "Error loading products: " + e.getMessage(),
                            "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        loadProducts.run();

        addButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                JDialog dialog = new JDialog((Frame) SwingUtilities.getWindowAncestor(panel), "Add New Product", true);
                dialog.setLayout(new GridLayout(6, 2, 5, 5));

                JTextField nameField = new JTextField();
                JTextField costField = new JTextField();
                JTextField priceField = new JTextField();

                JComboBox<String> supplierBox = new JComboBox<String>();
                JComboBox<String> categoryBox = new JComboBox<String>();

                // Populate supplier dropdown
                try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT company FROM suppliers")) {
                    while (rs.next()) {
                        supplierBox.addItem(rs.getString("company"));
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Error loading suppliers: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }

                // Populate category dropdown
                try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT category FROM products")) {
                    while (rs.next()) {
                        categoryBox.addItem(rs.getString("category"));
                    }
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Error loading categories: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }

                dialog.add(new JLabel("Product Name:"));
                dialog.add(nameField);
                dialog.add(new JLabel("Standard Cost:"));
                dialog.add(costField);
                dialog.add(new JLabel("List Price:"));
                dialog.add(priceField);
                dialog.add(new JLabel("Supplier:"));
                dialog.add(supplierBox);
                dialog.add(new JLabel("Category:"));
                dialog.add(categoryBox);

                JButton submit = new JButton("Add");
                JButton cancel = new JButton("Cancel");

                dialog.add(submit);
                dialog.add(cancel);

                submit.addActionListener(new ActionListener() {
                    public void actionPerformed(ActionEvent e) {
                        String name = nameField.getText();
                        String costText = costField.getText();
                        String priceText = priceField.getText();
                        String supplier = (String) supplierBox.getSelectedItem();
                        String category = (String) categoryBox.getSelectedItem();

                        try {
                            BigDecimal cost = new BigDecimal(costText);
                            BigDecimal price = new BigDecimal(priceText);

                            try (Connection conn = DatabaseConnection.getConnection()) {
                                // Get supplier ID
                                PreparedStatement psSupp = conn.prepareStatement("SELECT id FROM suppliers WHERE company = ?");
                                psSupp.setString(1, supplier);
                                ResultSet rsSupp = psSupp.executeQuery();
                                int supplierId = -1;
                                if (rsSupp.next()) {
                                    supplierId = rsSupp.getInt("id");
                                }

                                // Insert new product
                                PreparedStatement insert = conn.prepareStatement(
                                    "INSERT INTO products (product_name, supplier_ids, category, standard_cost, list_price) VALUES (?, ?, ?, ?, ?)"
                                );
                                insert.setString(1, name);
                                insert.setInt(2, supplierId);
                                insert.setString(3, category); 
                                insert.setBigDecimal(4, cost);
                                insert.setBigDecimal(5, price);

                                insert.executeUpdate();
                            }

                            dialog.dispose();
                            loadProducts.run(); // reload table
                        } catch (Exception ex) {
                            JOptionPane.showMessageDialog(dialog, "Error adding product: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                        }
                    }
                });

                cancel.addActionListener(ae -> dialog.dispose());

                dialog.pack();
                dialog.setLocationRelativeTo(panel);
                dialog.setVisible(true);
            }
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(addButton);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }


    private JPanel createCustomerTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columnNames = {"ID", "Company", "Contact Name", "Email", "City", "Country"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT id, company, CONCAT(first_name, ' ', last_name) as contact_name, " +
                     "email_address, city, country_region FROM customers")) {
            
            while (rs.next()) {
                model.addRow(new Object[]{
                    rs.getInt("id"),
                    rs.getString("company"),
                    rs.getString("contact_name"),
                    rs.getString("email_address"),
                    rs.getString("city"),
                    rs.getString("country_region")
                });
            }
        } catch (SQLException e) {
            JOptionPane.showMessageDialog(this, "Error loading customers: " + e.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
        }

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    public JPanel createOrderTab() {
        JPanel panel = new JPanel(new BorderLayout());
        String[] columnNames = {"ID", "Order Date", "Customer", "Employee", "Total Amount"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        // Refresh button at the top
        JButton refreshButton = new JButton("Refresh Orders");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        panel.add(buttonPanel, BorderLayout.NORTH);

        // Function to load order data into the table
        Runnable loadOrders = () -> {
            model.setRowCount(0); // Clear existing rows
            try (Connection conn = DatabaseConnection.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(
                     "SELECT o.id, o.order_date, c.company as customer, " +
                     "CONCAT(e.first_name, ' ', e.last_name) as employee, " +
                     "SUM(od.quantity * od.unit_price) as total_amount " +
                     "FROM orders o " +
                     "JOIN customers c ON o.customer_id = c.id " +
                     "JOIN employees e ON o.employee_id = e.id " +
                     "JOIN order_details od ON o.id = od.order_id " +
                     "GROUP BY o.id")) {

                // Check if we get any data from the result set
                while (rs.next()) {
                    // Debugging output
                    System.out.println(rs.getInt("id") + " " +
                            rs.getTimestamp("order_date") + " " +
                            rs.getString("customer") + " " +
                            rs.getString("employee") + " " +
                            rs.getBigDecimal("total_amount"));

                    model.addRow(new Object[]{
                        rs.getInt("id"),
                        rs.getTimestamp("order_date"),
                        rs.getString("customer"),
                        rs.getString("employee"),
                        rs.getBigDecimal("total_amount")
                    });
                }
            } catch (SQLException e) {
                // Log and show an error message
                e.printStackTrace();
                JOptionPane.showMessageDialog(panel, "Error loading orders: " + e.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        };

        // Load orders initially when the tab is created
        loadOrders.run();

        // Refresh button action
        refreshButton.addActionListener(e -> loadOrders.run());

        // Add the table scroll pane to the panel
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }


    private JPanel createReportTab() {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea reportArea = new JTextArea();
        reportArea.setEditable(false);
        JScrollPane scrollPane = new JScrollPane(reportArea);

        // Create a refresh button
        JButton refreshButton = new JButton("Refresh Report");
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.add(refreshButton);
        panel.add(buttonPanel, BorderLayout.NORTH);

        // Function to load and display the report
        Runnable loadReport = () -> {
            try (Connection conn = DatabaseConnection.getConnection()) {
                StringBuilder report = new StringBuilder();
                
                // Warehouse inventory by category report
                report.append("=== Inventory Summary by Product Category ===\n");
                report.append(String.format("%-20s %-15s %-10s\n", "Transaction Type", "Category", "Quantity"));
                report.append("-------------------------------------------------\n");
                
                // Query to get inventory counts by category and transaction type
                try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                        "SELECT itt.type_name, p.category, SUM(it.quantity) as total_quantity " +
                        "FROM inventory_transactions it " +
                        "JOIN inventory_transaction_types itt ON it.transaction_type = itt.id " +
                        "JOIN products p ON it.product_id = p.id " +
                        "GROUP BY itt.type_name, p.category " +
                        "ORDER BY itt.type_name, p.category")) {
                    
                    while (rs.next()) {
                        report.append(String.format("%-20s %-15s %-10d\n",
                            rs.getString("type_name"),
                            rs.getString("category"),
                            rs.getInt("total_quantity")));
                    }
                }
                
                // Additional report: Products by category
                report.append("\n\n=== Product Count by Category ===\n");
                report.append(String.format("%-20s %-10s\n", "Category", "Count"));
                report.append("-----------------------------\n");
                
                try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                        "SELECT category, COUNT(*) as product_count " +
                        "FROM products " +
                        "GROUP BY category " +
                        "ORDER BY category")) {
                    
                    while (rs.next()) {
                        report.append(String.format("%-20s %-10d\n",
                            rs.getString("category"),
                            rs.getInt("product_count")));
                    }
                }
                
                // Additional report: Inventory transaction summary
                report.append("\n\n=== Inventory Transaction Summary ===\n");
                report.append(String.format("%-20s %-10s\n", "Transaction Type", "Count"));
                report.append("-----------------------------\n");
                
                try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery(
                        "SELECT itt.type_name, COUNT(*) as transaction_count " +
                        "FROM inventory_transactions it " +
                        "JOIN inventory_transaction_types itt ON it.transaction_type = itt.id " +
                        "GROUP BY itt.type_name")) {
                    
                    while (rs.next()) {
                        report.append(String.format("%-20s %-10d\n",
                            rs.getString("type_name"),
                            rs.getInt("transaction_count")));
                    }
                }
                
                reportArea.setText(report.toString());
            } catch (SQLException e) {
                reportArea.setText("Error generating reports: " + e.getMessage());
            }
        };

        // Load report initially
        loadReport.run();

        // Refresh report when button is clicked
        refreshButton.addActionListener(e -> loadReport.run());

        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private JPanel createNotificationsTab() {
        JPanel panel = new JPanel(new BorderLayout());

        String[] columnNames = {"ID", "Company", "First Name", "Last Name", "Email", "Phone"};
        DefaultTableModel model = new DefaultTableModel(columnNames, 0);
        JTable table = new JTable(model);
        JScrollPane scrollPane = new JScrollPane(table);

        Runnable loadCustomers = new Runnable() {
            public void run() {
                model.setRowCount(0);
                try (Connection conn = DatabaseConnection.getConnection();
                    Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT id, company, first_name, last_name, email_address, business_phone FROM customers")) {
                    while (rs.next()) {
                        model.addRow(new Object[]{
                            rs.getInt("id"),
                            rs.getString("company"),
                            rs.getString("first_name"),
                            rs.getString("last_name"),
                            rs.getString("email_address"),
                            rs.getString("business_phone")
                        });
                    }
                } catch (SQLException e) {
                    JOptionPane.showMessageDialog(panel, "Error loading customers: " + e.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        loadCustomers.run();

        JButton addButton = new JButton("Add Customer");
        JButton updateButton = new JButton("Update Selected");
        JButton deleteButton = new JButton("Delete Selected");

        addButton.addActionListener(e -> openCustomerDialog(null, model, loadCustomers));
        updateButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected == -1) {
                JOptionPane.showMessageDialog(panel, "Please select a customer to update.");
                return;
            }
            Object[] rowData = new Object[model.getColumnCount()];
            for (int i = 0; i < model.getColumnCount(); i++) {
                rowData[i] = model.getValueAt(selected, i);
            }
            openCustomerDialog(rowData, model, loadCustomers);
        });

        deleteButton.addActionListener(e -> {
            int selected = table.getSelectedRow();
            if (selected == -1) {
                JOptionPane.showMessageDialog(panel, "Please select a customer to delete.");
                return;
            }

            int confirm = JOptionPane.showConfirmDialog(panel, "Are you sure you want to delete this customer?", "Confirm", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) return;

            int customerId = (Integer) model.getValueAt(selected, 0);
            try (Connection conn = DatabaseConnection.getConnection();
                PreparedStatement stmt = conn.prepareStatement("DELETE FROM customers WHERE id = ?")) {
                stmt.setInt(1, customerId);
                stmt.executeUpdate();
                loadCustomers.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Error deleting customer: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        topPanel.add(addButton);
        topPanel.add(updateButton);
        topPanel.add(deleteButton);

        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        return panel;
    }

    private void openCustomerDialog(Object[] rowData, DefaultTableModel model, Runnable reload) {
        JDialog dialog = new JDialog(this, rowData == null ? "Add Customer" : "Update Customer", true);
        dialog.setLayout(new GridLayout(7, 2, 5, 5));

        JTextField companyField = new JTextField();
        JTextField firstNameField = new JTextField();
        JTextField lastNameField = new JTextField();
        JTextField emailField = new JTextField();
        JTextField phoneField = new JTextField();

        if (rowData != null) {
            companyField.setText((String) rowData[1]);
            firstNameField.setText((String) rowData[2]);
            lastNameField.setText((String) rowData[3]);
            emailField.setText((String) rowData[4]);
            phoneField.setText((String) rowData[5]);
        }

        dialog.add(new JLabel("Company:"));
        dialog.add(companyField);
        dialog.add(new JLabel("First Name:"));
        dialog.add(firstNameField);
        dialog.add(new JLabel("Last Name:"));
        dialog.add(lastNameField);
        dialog.add(new JLabel("Email:"));
        dialog.add(emailField);
        dialog.add(new JLabel("Phone:"));
        dialog.add(phoneField);

        JButton submit = new JButton(rowData == null ? "Add" : "Update");
        JButton cancel = new JButton("Cancel");
        dialog.add(submit);
        dialog.add(cancel);

        submit.addActionListener(e -> {
            String company = companyField.getText();
            String fname = firstNameField.getText();
            String lname = lastNameField.getText();
            String email = emailField.getText();
            String phone = phoneField.getText();

            if (company.isEmpty() || fname.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Company and First Name are required.");
                return;
            }

            try (Connection conn = DatabaseConnection.getConnection()) {
                if (rowData == null) {
                    PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO customers (company, first_name, last_name, email_address, business_phone) VALUES (?, ?, ?, ?, ?)"
                    );
                    insert.setString(1, company);
                    insert.setString(2, fname);
                    insert.setString(3, lname);
                    insert.setString(4, email);
                    insert.setString(5, phone);
                    insert.executeUpdate();
                } else {
                    int customerId = (Integer) rowData[0];
                    PreparedStatement update = conn.prepareStatement(
                        "UPDATE customers SET company = ?, first_name = ?, last_name = ?, email = ?, business_phone = ? WHERE id = ?"
                    );
                    update.setString(1, company);
                    update.setString(2, fname);
                    update.setString(3, lname);
                    update.setString(4, email);
                    update.setString(5, phone);
                    update.setInt(6, customerId);
                    update.executeUpdate();
                }

                dialog.dispose();
                reload.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(dialog, "Error saving customer: " + ex.getMessage(), "Database Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        cancel.addActionListener(ae -> dialog.dispose());

        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GUI());
    }
}
