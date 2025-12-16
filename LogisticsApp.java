import java.io.*;
import java.util.*;

/**
 * LogisticsApp.java
 *
 * Single-file Transport Logistics System implementing:
 * - Vehicles (abstract) -> Truck, Van
 * - Shipments, Routes
 * - RoutePlanner (interface) + BasicRoutePlanner
 * - Strategy Pattern (cost models)
 * - Factory Pattern (planner factory)
 * - Map<Route, Double> and PriorityQueue for optimization
 * - CSV loading (routes.csv, fleet.csv)
 * - CLI menu, plan.txt output
 *
 * Author: ChatGPT (example)
 */

//////////////////////////
// Custom Exceptions
//////////////////////////
class RouteNotFoundException extends Exception {
    public RouteNotFoundException(String msg) { super(msg); }
}

class OverCapacityException extends Exception {
    public OverCapacityException(String msg) { super(msg); }
}

//////////////////////////
// Vehicle Hierarchy
//////////////////////////
abstract class Vehicle {
    String vehicleId;
    String vehicleName;
    String driverName;
    double capacity;   // kg
    double mileage;    // km per liter
    double rate;       // fuel rate per liter (currency)

    public Vehicle(String id, String name, String driver, double capacity, double mileage, double rate) {
        this.vehicleId = id;
        this.vehicleName = name;
        this.driverName = driver;
        this.capacity = capacity;
        this.mileage = mileage;
        this.rate = rate;
    }

    abstract double efficiencyFactor(); // factor to adjust mileage

    void displayBox() {
        System.out.println("+----------------------+");
        System.out.println("|       Vehicle        |");
        System.out.println("+----------------------+");
        System.out.printf("| vehicleId   : %s%n", vehicleId);
        System.out.printf("| vehicleName : %s%n", vehicleName);
        System.out.printf("| driverName  : %s%n", driverName);
        System.out.printf("| capacity    : %.1f kg%n", capacity);
        System.out.printf("| mileage     : %.2f km/l%n", mileage);
        System.out.printf("| rate (L)    : %.2f%n", rate);
        System.out.println("+----------------------+");
    }
}

class Truck extends Vehicle {
    public Truck(String id, String name, String driver, double capacity, double mileage, double rate) {
        super(id, name, driver, capacity, mileage, rate);
    }
    double efficiencyFactor() { return 0.9; } // trucks less efficient
}

class Van extends Vehicle {
    public Van(String id, String name, String driver, double capacity, double mileage, double rate) {
        super(id, name, driver, capacity, mileage, rate);
    }
    double efficiencyFactor() { return 1.05; } // vans slightly better
}

//////////////////////////
// Shipment
//////////////////////////
class Shipment {
    String shipmentId;
    double weight;   // kg
    double distance; // km
    double toll;     // fixed toll for route (optional)
    double costPerKmOverride; // if you want direct cost per km instead of computed

    public Shipment(String shipmentId, double weight, double distance, double toll, double costPerKmOverride) {
        this.shipmentId = shipmentId;
        this.weight = weight;
        this.distance = distance;
        this.toll = toll;
        this.costPerKmOverride = costPerKmOverride;
    }

    double calculateCostSimple() {
        return distance * costPerKmOverride;
    }

    double estimateTimeHours(double avgSpeedKmPerHr) {
        return distance / avgSpeedKmPerHr;
    }

    void displayBox() {
        System.out.println("+----------------------+");
        System.out.println("|      Shipment        |");
        System.out.println("+----------------------+");
        System.out.printf("| shipmentId  : %s%n", shipmentId);
        System.out.printf("| weight      : %.1f kg%n", weight);
        System.out.printf("| distance    : %.1f km%n", distance);
        System.out.printf("| toll        : %.2f%n", toll);
        if (costPerKmOverride > 0) System.out.printf("| costPerKm   : %.2f%n", costPerKmOverride);
        System.out.println("+----------------------+");
    }
}

//////////////////////////
// Route
//////////////////////////
class Route {
    String routeId;
    String source;
    String destination;
    double distance;
    double toll; // route-level tolls

    public Route(String routeId, String source, String destination, double distance, double toll) {
        this.routeId = routeId;
        this.source = source;
        this.destination = destination;
        this.distance = distance;
        this.toll = toll;
    }

    void displayBox() {
        System.out.println("+----------------------+");
        System.out.println("|        Route         |");
        System.out.println("+----------------------+");
        System.out.printf("| routeId     : %s%n", routeId);
        System.out.printf("| from -> to  : %s -> %s%n", source, destination);
        System.out.printf("| distance    : %.1f km%n", distance);
        System.out.printf("| toll        : %.2f%n", toll);
        System.out.println("+----------------------+");
    }

    // equality by routeId to enable Map<Route, Double>
    @Override
    public boolean equals(Object o) {
        if (!(o instanceof Route)) return false;
        return ((Route) o).routeId.equals(this.routeId);
    }

    @Override
    public int hashCode() {
        return routeId.hashCode();
    }
}

//////////////////////////
// Cost Strategy Pattern
//////////////////////////
interface CostStrategy {
    /**
     * Compute cost for given distance & vehicle & route & shipment
     * Returns total monetary cost
     */
    double computeCost(Route route, Vehicle vehicle, Shipment shipment);
}

/** Basic fuel-only cost: fuel liters needed * fuel price */
class FuelCostStrategy implements CostStrategy {
    public double computeCost(Route route, Vehicle vehicle, Shipment shipment) {
        double dist = route.distance;
        double effectiveMileage = vehicle.mileage * vehicle.efficiencyFactor();
        double liters = dist / effectiveMileage;
        double fuelCost = liters * vehicle.rate;
        // add any shipment-level tolls and route tolls
        return fuelCost + shipment.toll + route.toll;
    }
}

/** Fuel + fixed surcharge (e.g., driver allowance) + optional toll modeling */
class FuelAndTollStrategy implements CostStrategy {
    double driverAllowancePerHr;
    double avgSpeed;

    public FuelAndTollStrategy(double driverAllowancePerHr, double avgSpeed) {
        this.driverAllowancePerHr = driverAllowancePerHr;
        this.avgSpeed = avgSpeed;
    }

    public double computeCost(Route route, Vehicle vehicle, Shipment shipment) {
        double dist = route.distance;
        double effectiveMileage = vehicle.mileage * vehicle.efficiencyFactor();
        double liters = dist / effectiveMileage;
        double fuelCost = liters * vehicle.rate;

        double hours = dist / avgSpeed;
        double allowance = hours * driverAllowancePerHr;

        // optionally account extra cost for overweight (very simple model)
        double overweightPenalty = 0;
        if (shipment.weight > vehicle.capacity) {
            overweightPenalty = 1000; // large penalty for invalid assignment (should be blocked earlier)
        }

        return fuelCost + allowance + shipment.toll + route.toll + overweightPenalty;
    }
}

//////////////////////////
// RoutePlanner interface + BasicRoutePlanner
//////////////////////////
interface RoutePlanner {
    /**
     * Compute cost for assigning given shipment to given vehicle on given route
     * Throws exceptions for invalid route/over capacity
     */
    double computeRouteCost(String routeId, String vehicleId, String shipmentId)
            throws RouteNotFoundException, OverCapacityException;
    Map<Route, Double> computeCostsForAllRoutes(String vehicleId, String shipmentId);
}

class BasicRoutePlanner implements RoutePlanner {
    Map<String, Route> routesById;
    Map<String, Vehicle> fleetById;
    Map<String, Shipment> shipmentsById;
    CostStrategy strategy;

    public BasicRoutePlanner(Map<String, Route> routesById,
                             Map<String, Vehicle> fleetById,
                             Map<String, Shipment> shipmentsById,
                             CostStrategy strategy) {
        this.routesById = routesById;
        this.fleetById = fleetById;
        this.shipmentsById = shipmentsById;
        this.strategy = strategy;
    }

    public double computeRouteCost(String routeId, String vehicleId, String shipmentId)
            throws RouteNotFoundException, OverCapacityException {

        if (!routesById.containsKey(routeId)) throw new RouteNotFoundException("Route not found: " + routeId);
        if (!fleetById.containsKey(vehicleId)) throw new RouteNotFoundException("Vehicle not found: " + vehicleId);
        if (!shipmentsById.containsKey(shipmentId)) throw new RouteNotFoundException("Shipment not found: " + shipmentId);

        Route r = routesById.get(routeId);
        Vehicle v = fleetById.get(vehicleId);
        Shipment s = shipmentsById.get(shipmentId);

        if (s.weight > v.capacity) throw new OverCapacityException("Shipment weight exceeds vehicle capacity.");

        return strategy.computeCost(r, v, s);
    }

    public Map<Route, Double> computeCostsForAllRoutes(String vehicleId, String shipmentId) {
        Map<Route, Double> map = new HashMap<>();
        for (Route r : routesById.values()) {
            try {
                double cost = computeRouteCost(r.routeId, vehicleId, shipmentId);
                map.put(r, cost);
            } catch (Exception e) {
                // skip invalid pairings (e.g., over capacity)
            }
        }
        return map;
    }
}

//////////////////////////
// Factory Pattern
//////////////////////////
class RoutePlannerFactory1 {
    public static RoutePlanner createPlanner(String type,
                                             Map<String, Route> routesById,
                                             Map<String, Vehicle> fleetById,
                                             Map<String, Shipment> shipmentsById) {
        switch (type.toLowerCase()) {
            case "fuelandtoll":
                // driverAllowancePerHr, avgSpeed
                return new BasicRoutePlanner(routesById, fleetById, shipmentsById, 
                		new FuelAndTollStrategy(100, 50));
            case "fuel":
            default:
                return new BasicRoutePlanner(routesById, fleetById, shipmentsById,
                		new FuelCostStrategy());
        }
    }
}

//////////////////////////
// CSV Utilities
//////////////////////////
class CSVUtils {
    // routes.csv format: routeId,source,destination,distance,toll
    static Map<String, Route> loadRoutesCSV(String filename) {
        Map<String, Route> map = new LinkedHashMap<>();
        File f = new File(filename);
        if (!f.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(",");
                // guard
                if (tokens.length < 5) continue;
                String id = tokens[0].trim();
                String src = tokens[1].trim();
                String dst = tokens[2].trim();
                double dist = Double.parseDouble(tokens[3].trim());
                double toll = Double.parseDouble(tokens[4].trim());
                map.put(id, new Route(id, src, dst, dist, toll));
            }
        } catch (Exception e) {
            System.out.println("Error loading routes.csv: " + e.getMessage());
        }
        return map;
    }

    // fleet.csv format: vehicleId,type,vehicleName,driverName,capacity,mileage,rate
    static Map<String, Vehicle> loadFleetCSV(String filename) {
        Map<String, Vehicle> map = new LinkedHashMap<>();
        File f = new File(filename);
        if (!f.exists()) return map;
        try (BufferedReader br = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                String[] tokens = line.split(",");
                if (tokens.length < 7) continue;
                String id = tokens[0].trim();
                String type = tokens[1].trim();
                String vname = tokens[2].trim();
                String dname = tokens[3].trim();
                double cap = Double.parseDouble(tokens[4].trim());
                double mileage = Double.parseDouble(tokens[5].trim());
                double rate = Double.parseDouble(tokens[6].trim());
                Vehicle v = type.equalsIgnoreCase("truck")
                        ? new Truck(id, vname, dname, cap, mileage, rate)
                        : new Van(id, vname, dname, cap, mileage, rate);
                map.put(id, v);
            }
        } catch (Exception e) {
            System.out.println("Error loading fleet.csv: " + e.getMessage());
        }
        return map;
    }
}

//////////////////////////
// Main Application (CLI)
//////////////////////////
public class LogisticsApp {
	static Scanner sc = new Scanner(System.in);

    // In-memory stores
    static Map<String, Vehicle> fleet = new LinkedHashMap<>();
    static Map<String, Route> routes = new LinkedHashMap<>();
    static Map<String, Shipment> shipments = new LinkedHashMap<>();
    

    public static void main(String[] args) {
        // try load CSVs if present
        routes.putAll(CSVUtils.loadRoutesCSV("routes.csv"));
        fleet.putAll(CSVUtils.loadFleetCSV("fleet.csv"));

        System.out.println("=== Transport Logistics System ===");
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = sc.nextLine().trim();
            switch (choice) {
                case "1":
                	addVehicle(); 
                	break;
                case "2": 
                	addRoute(); 
                	break;
                case "3": 
                	addShipment(); 
                	break;
                case "4": 
                	listAll(); 
                	break;
                case "5": 
                	computeCostForPair(); 
                	break;
                case "6": 
                	optimizeAssignmentAndWritePlan(); 
                	break;
                case "7": 
                	loadSampleCSVFiles(); 
                	break;
                case "0": 
                	System.out.println("Exiting..."); 
                	running = false; break;
                default: 
                	System.out.println("Invalid choice. Try again.");
            }
        }
    }

    static void printMainMenu() {
        System.out.println("\nMenu:");
        System.out.println("1. Add Vehicle");
        System.out.println("2. Add Route");
        System.out.println("3. Add Shipment");
        System.out.println("4. List Vehicles / Routes / Shipments");
        System.out.println("5. Compute Cost (route + vehicle + shipment)");
        System.out.println("6. Optimize (find cheapest route/vehicle for a shipment) & write plan.txt");
        System.out.println("7. Create sample CSVs (routes.csv & fleet.csv) in current folder");
        System.out.println("0. Exit");
        System.out.print("Enter choice: ");
    }

    static void addVehicle() {
        System.out.print("Vehicle ID: "); 
        String id = sc.nextLine().trim();
        System.out.print("Type (truck/van): "); 
        String type = sc.nextLine().trim();
        System.out.print("Vehicle Name: "); 
        String name = sc.nextLine().trim();
        System.out.print("Driver Name: "); 
        String driver = sc.nextLine().trim();
        System.out.print("Capacity (kg): "); 
        double cap = readDouble();
        System.out.print("Mileage (km per liter): "); 
        double mileage = readDouble();
        System.out.print("Fuel rate per liter: "); 
        double rate = readDouble();

        Vehicle v = type.equalsIgnoreCase("truck")
                ? new Truck(id, name, driver, cap, mileage, rate)
                : new Van(id, name, driver, cap, mileage, rate);
        fleet.put(id, v);
        System.out.println("Vehicle added.");
    }

    static void addRoute() {
        System.out.print("Route ID: "); 
        String id = sc.nextLine().trim();
        System.out.print("Source: ");
        String src = sc.nextLine().trim();
        System.out.print("Destination: "); 
        String dst = sc.nextLine().trim();
        System.out.print("Distance (km): ");
        double dist = readDouble();
        System.out.print("Route toll (currency): "); 
        double toll = readDouble();
        routes.put(id, new Route(id, src, dst, dist, toll));
        System.out.println("Route added.");
    }

    static void addShipment() {
        System.out.print("Shipment ID: "); 
        String id = sc.nextLine().trim();
        System.out.print("Weight (kg): "); 
        double weight = readDouble();
        System.out.print("Distance (km): "); 
        double dist = readDouble();
        System.out.print("Shipment toll (currency): "); 
        double toll = readDouble();
        System.out.print("CostPerKm override (0 to skip): "); 
        double cpk = readDouble();
        shipments.put(id, new Shipment(id, weight, dist, toll, cpk));
        System.out.println("Shipment added.");
    }

    static void listAll() {
        System.out.println("\n--- Vehicles ---");
        if (fleet.isEmpty()) System.out.println("[no vehicles]");
        for (Vehicle v : fleet.values()) v.displayBox();

        System.out.println("\n--- Routes ---");
        if (routes.isEmpty()) System.out.println("[no routes]");
        for (Route r : routes.values()) r.displayBox();

        System.out.println("\n--- Shipments ---");
        if (shipments.isEmpty()) System.out.println("[no shipments]");
        for (Shipment s : shipments.values()) s.displayBox();
    }

    static void computeCostForPair() {
        System.out.print("Enter Route ID: "); 
        String rid = sc.nextLine().trim();
        System.out.print("Enter Vehicle ID: "); 
        String vid = sc.nextLine().trim();
        System.out.print("Enter Shipment ID: "); 
        String sid = sc.nextLine().trim();

        // choose strategy type
        System.out.print("Strategy (fuel / fuelandtoll): "); 
        String st = sc.nextLine().trim();
        RoutePlanner planner = RoutePlannerFactory1.createPlanner(st, routes, fleet, shipments);

        try {
            double cost = planner.computeRouteCost(rid, vid, sid);
            System.out.printf("Computed Cost = %.2f%n", cost);
        } catch (Exception e) {
            System.out.println("Error computing cost: " + e.getMessage());
        }
    }

    static void optimizeAssignmentAndWritePlan() {
        if (shipments.isEmpty()) {
            System.out.println("No shipments available to optimize.");
            return;
        }
        System.out.print("Enter Shipment ID to optimize: "); 
        String sid = sc.nextLine().trim();
        if (!shipments.containsKey(sid)) {
        	System.out.println("Shipment not found."); return; }

        if (fleet.isEmpty() || routes.isEmpty()) {
            System.out.println("Need at least one vehicle and one route for optimization.");
            return;
        }

        // choose strategy
        System.out.print("Strategy (fuel / fuelandtoll): ");
        String st = sc.nextLine().trim();
        RoutePlanner planner = RoutePlannerFactory1.createPlanner(st, routes, fleet, shipments);

        // For each vehicle, compute costs for all routes and push into PQ
        PriorityQueue<Map.Entry<Route, Double>> pq = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue)
        );

        Map<Route, Double> chosenCosts = new HashMap<>();

        for (String vid : fleet.keySet()) {
            Map<Route, Double> costs = planner.computeCostsForAllRoutes(vid, sid);
            for (Map.Entry<Route, Double> e : costs.entrySet()) {
                pq.offer(new AbstractMap.SimpleEntry<>(e.getKey(), e.getValue()));
            }
        }

        if (pq.isEmpty()) {
            System.out.println("No valid route-vehicle combination found (maybe overweight).");
            return;
        }

        Map.Entry<Route, Double> best = pq.poll();
        Route bestRoute = best.getKey();
        double bestCost = best.getValue();

        // find a vehicle that can run this route & shipment (first matching)
        String assignedVehicleId = null;
        for (Vehicle v : fleet.values()) {
            try {
                double c = planner.computeRouteCost(bestRoute.routeId, v.vehicleId, sid);
                assignedVehicleId = v.vehicleId;
                break;
            } catch (Exception ignored) {}
        }

        // Write plan.txt
        try (PrintWriter pw = new PrintWriter(new FileWriter("plan.txt"))) {
            pw.println("=== OPTIMIZED TRANSPORT PLAN ===");
            pw.printf("Shipment ID      : %s%n", sid);
            pw.printf("Assigned Vehicle : %s%n", assignedVehicleId == null ? "N/A" : assignedVehicleId);
            pw.printf("Route ID         : %s (%s -> %s)%n", bestRoute.routeId, bestRoute.source, bestRoute.destination);
            pw.printf("Distance (km)    : %.2f%n", bestRoute.distance);
            pw.printf("Estimated Cost   : %.2f%n", bestCost);
            pw.printf("Route Toll       : %.2f%n", bestRoute.toll);
            pw.printf("Shipment Toll    : %.2f%n", shipments.get(sid).toll);
            pw.println("===============================");
            System.out.println("Optimized plan written to plan.txt");
        } catch (Exception e) {
            System.out.println("Error writing plan.txt: " + e.getMessage());
        }

        // Also print formatted boxes on console
        System.out.println("\n=== Best Assignment ===");
        if (assignedVehicleId != null) fleet.get(assignedVehicleId).displayBox();
        bestRoute.displayBox();
        shipments.get(sid).displayBox();
        System.out.println("+-----------------------------+");
        System.out.println("|     LogisticsSystem         |");
        System.out.println("+-----------------------------+");
        System.out.printf("| Optimized Cost : %.2f%n", bestCost);
        System.out.println("+-----------------------------+");
    }

    static void loadSampleCSVFiles() {
        // Write sample routes.csv and fleet.csv to current dir
        try (PrintWriter pr = new PrintWriter(new FileWriter("routes.csv"))) {
            pr.println("R1,CityA,CityB,300,50");
            pr.println("R2,CityA,CityC,450,75");
            pr.println("R3,CityB,CityC,150,20");
        } catch (Exception e) {
            System.out.println("Error writing sample routes.csv: " + e.getMessage());
        }

        try (PrintWriter pr = new PrintWriter(new FileWriter("fleet.csv"))) {
            pr.println("V1,truck,VolvoTruck,John,5000,3.5,90");
            pr.println("V2,van,TataAce,Ramesh,1200,12.0,90");
            pr.println("V3,truck,MahindraLoad,Anil,4000,4.5,90");
        } catch (Exception e) {
            System.out.println("Error writing sample fleet.csv: " + e.getMessage());
        }

        System.out.println("Sample CSV files created (routes.csv, fleet.csv). They will be loaded now.");
        // reload
        routes.clear(); routes.putAll(CSVUtils.loadRoutesCSV("routes.csv"));
        fleet.clear(); fleet.putAll(CSVUtils.loadFleetCSV("fleet.csv"));
    }

    // helper to read double safely
    static double readDouble() {
        while (true) {
            String s = sc.nextLine().trim();
            try {
                if (s.isEmpty()) return 0;
                return Double.parseDouble(s);
            } catch (NumberFormatException e) {
                System.out.print("Invalid number, try again: ");
            }
        }
    }
}

