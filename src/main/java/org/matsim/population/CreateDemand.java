package org.matsim.population;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.locationtech.jts.geom.*;
import org.matsim.api.core.v01.Coord;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.*;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.PopulationUtils;
import org.matsim.core.utils.gis.ShapeFileReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;

class CreateDemand {

	private static final Logger logger = LogManager.getLogger("CreateDemand");
	private static final String REGION_KEY = "osm_id";
	private static final String HOME_AND_WORK_REGION = "Number";
	private static final int HOME_END_TIME = 8 * 60 * 60;
	private static final int WORK_END_TIME = 17 * 60 * 60 + 30 * 60;
	private static final double SCALE_FACTOR = 0.1;
	private static final GeometryFactory geometryFactory = new GeometryFactory();
	private static final CSVFormat csvFormat = CSVFormat.Builder.create()
			.setDelimiter(',')
			.setHeader()
			.setAllowMissingColumnNames(true)
			.build();
	private final Map<String, Geometry> regions;
	private final Path innerRegionCommuterStatistic;
	private final Random random = new Random();
	private Population population;

	CreateDemand() {
		Path sampleFolder = Paths.get("scenarios/osm");
		this.innerRegionCommuterStatistic = sampleFolder.resolve("osm.csv");

		regions = ShapeFileReader.getAllFeatures(sampleFolder.resolve("osm.shp").toString()).stream()
				.collect(Collectors.toMap(feature -> (String) feature.getAttribute("osm_id").toString(),
						feature -> (Geometry) feature.getDefaultGeometry()));

		this.population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
	}

	Population getPopulation() {
		return this.population;
	}

	void create() {
		population = PopulationUtils.createPopulation(ConfigUtils.createConfig());
		createCommuters();
		logger.info("Done.");
	}

	private void createCommuters() {

		logger.info("Creating regional commuters.");
		try (CSVParser parser = CSVParser.parse(innerRegionCommuterStatistic, StandardCharsets.UTF_8, csvFormat)) {
			for (CSVRecord record : parser) {
				String region = record.get(REGION_KEY);
				if (regions.containsKey(region)) {
					int numberOfCommuters = tryParseValue(record.get(HOME_AND_WORK_REGION));
					createPersons(region, region, numberOfCommuters);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private void createPersons(String homeRegionKey, String workRegionKey, int numberOfPersons) {

		if (!regions.containsKey(homeRegionKey) || !regions.containsKey(workRegionKey)) return;
		logger.info("Home region: " + homeRegionKey + " work region: " + workRegionKey + " number of commuters: " +
				numberOfPersons);
		Geometry homeRegion = regions.get(homeRegionKey);
		Geometry workRegion = regions.get(workRegionKey);
		for (int i = 0; i < numberOfPersons * SCALE_FACTOR; i++) {
			Coord home = getCoordInGeometry(homeRegion);
			Coord work = getCoordInGeometry(workRegion);
			String id = homeRegionKey + "_" + workRegionKey + "_" + i;
			Person person = createPerson(home, work, TransportMode.car, id);
			population.addPerson(person);
		}
	}

	private Person createPerson(Coord home, Coord work, String mode, String id) {
		Person person = population.getFactory().createPerson(Id.createPersonId(id));
		Plan plan = createPlan(home, work, mode);
		person.addPlan(plan);
		return person;
	}

	private Plan createPlan(Coord home, Coord work, String mode) {

		Plan plan = population.getFactory().createPlan();
		Activity homeActivityInTheMorning = population.getFactory().createActivityFromCoord("home", home);
		homeActivityInTheMorning.setEndTime(HOME_END_TIME);
		plan.addActivity(homeActivityInTheMorning);

		Leg toWork = population.getFactory().createLeg(mode);
		plan.addLeg(toWork);
		Activity workActivity = population.getFactory().createActivityFromCoord("work", work);
		workActivity.setEndTime(WORK_END_TIME);
		plan.addActivity(workActivity);

		Leg toHome = population.getFactory().createLeg(mode);
		plan.addLeg(toHome);

		Activity homeActivityInTheEvening = population.getFactory().createActivityFromCoord("home", home);
		plan.addActivity(homeActivityInTheEvening);

		return plan;
	}


private Coord getCoordInGeometry(Geometry region) {

	double x, y;
	Point point;
	do {
		Envelope envelope = region.getEnvelopeInternal();
		x = envelope.getMinX() + envelope.getWidth() * random.nextDouble();
		y = envelope.getMinY() + envelope.getHeight() * random.nextDouble();
		// Random coordinate for building.shp
		// x = envelope.getMinX() + Math.random() * envelope.getWidth();
        // y = envelope.getMinY() + Math.random() * envelope.getHeight();
		point = geometryFactory.createPoint(new Coordinate(x, y));
	} while (point == null || !region.contains(point));

	return new Coord(x, y);
}

	private int tryParseValue(String value) {

		// first remove things excel may have put into the value
		value = value.replace(",", "");

		try {
			return Integer.parseInt(value);
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}

