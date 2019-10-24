package solar;

import ws.palladian.helper.collection.CollectionHelper;
import ws.palladian.persistence.DatabaseManager;
import ws.palladian.persistence.DatabaseManagerFactory;
import ws.palladian.persistence.RowConverters;

import javax.sql.DataSource;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class SolarDatabaseManager extends DatabaseManager {

    private static final String WATTAGE = "SELECT (Pac1 + Pac2 + Pac3) pSum FROM SpotData ORDER BY TIMESTAMP DESC LIMIT 0,1";
    private static final String WATTAGE_AVG = "SELECT AVG(Pac1 + Pac2 + Pac3) avgPSum FROM SpotData WHERE TIMESTAMP > ?";


    public static SolarDatabaseManager getSDBM(){
        return DatabaseManagerFactory.create(SolarDatabaseManager.class, "jdbc:mysql://localhost:3306/SBFspot?useServerPrepStmts=false&cachePrepStmts=false&useUnicode=true&characterEncoding=UTF-8&sessionVariables=group_concat_max_len=16777216", "SBFspotUser", "SBFspotPassword");
    }

    protected SolarDatabaseManager(DataSource dataSource) {
        super(dataSource);
    }

    public Double getWattage() {
        List<Double> doubles = runQuery(RowConverters.DOUBLE, WATTAGE);
        return Optional.ofNullable(CollectionHelper.getFirst(doubles)).orElse(0.0);
    }

    public Double getWattageAvg() {
        long pointInTime = TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis());
        long fiveMinutes = TimeUnit.MINUTES.toSeconds(5);
        pointInTime = pointInTime - fiveMinutes;
        List<Double> doubles = runQuery(RowConverters.DOUBLE, WATTAGE_AVG, pointInTime);
        return Optional.ofNullable(CollectionHelper.getFirst(doubles)).orElse(0.0);
    }

}
