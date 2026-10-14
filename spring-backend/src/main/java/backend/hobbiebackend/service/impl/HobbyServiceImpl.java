package backend.hobbiebackend.service.impl;

import backend.hobbiebackend.handler.NotFoundException;
import backend.hobbiebackend.model.entities.*;
import backend.hobbiebackend.model.entities.enums.CategoryNameEnum;
import backend.hobbiebackend.model.entities.enums.LocationEnum;
import backend.hobbiebackend.model.repostiory.HobbyRepository;
import backend.hobbiebackend.service.CategoryService;
import backend.hobbiebackend.service.EmbeddingService;
import backend.hobbiebackend.service.HobbyService;
import backend.hobbiebackend.service.LocationService;
import backend.hobbiebackend.service.UserService;
import com.cloudinary.Cloudinary;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class HobbyServiceImpl implements HobbyService {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final int MAX_MATCHES = 10;

    private final HobbyRepository hobbyRepository;
    private final CategoryService categoryService;
    private final UserService userService;
    private final LocationService locationService;
    private final Cloudinary cloudinary;
    private final EmbeddingService embeddingService;

    @Autowired
    public HobbyServiceImpl(HobbyRepository hobbyRepository, CategoryService categoryService, UserService userService, LocationService locationService, Cloudinary cloudinary, EmbeddingService embeddingService) {
        this.hobbyRepository = hobbyRepository;
        this.categoryService = categoryService;
        this.userService = userService;
        this.locationService = locationService;
        this.cloudinary = cloudinary;
        this.embeddingService = embeddingService;
    }

    @Override
    public Hobby findHobbieById(Long id) {
        Optional<Hobby> hobby = this.hobbyRepository.findById(id);
        if (hobby.isPresent()) {
            return hobby.get();
        } else {
            throw new NotFoundException("This hobby does not exist");
        }
    }

    @SneakyThrows
    @Override
    public void saveUpdatedHobby(Hobby hobby) {
        Optional<Hobby> byId = this.hobbyRepository.findById(hobby.getId());
        if (byId.isPresent()) {
            deleteResourcesById(byId.get());
        }
        applyEmbedding(hobby);
        this.hobbyRepository.save(hobby);
    }

    @Override
    public boolean deleteHobby(long id) throws Exception {
        Optional<Hobby> byId = this.hobbyRepository.findById(id);
        if (byId.isPresent()) {
            deleteResourcesById(byId.get());
            BusinessOwner business = this.userService.findBusinessByUsername(byId.get().getCreator());
            business.getHobby_offers().remove(byId.get());
            this.userService.findAndRemoveHobbyFromClientsRecords(byId.get());
            this.hobbyRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private void deleteResourcesById(Hobby byId) throws Exception {
        String profileImgId = byId.getProfileImg_id();
        String galleryImgId1 = byId.getGalleryImg1_id();
        String galleryImgId2 = byId.getGalleryImg2_id();
        String galleryImgId3 = byId.getGalleryImg3_id();

        cloudinary.api().deleteResources(Arrays.asList(profileImgId, galleryImgId1, galleryImgId2, galleryImgId3),
                Map.of("invalidate", true));
    }


    @Override
    public Set<Hobby> findHobbyMatches(String username) {
        AppClient currentUserAppClient = this.userService.findAppClientByUsername(username);
        Test testResults = currentUserAppClient.getTestResults();
        if (testResults == null) {
            return new LinkedHashSet<>();
        }

        Location locationByName = this.locationService.getLocationByName(testResults.getLocation());
        List<Hobby> allByLocation = this.hobbyRepository.findAllByLocation(locationByName);
        if (allByLocation.isEmpty()) {
            return new LinkedHashSet<>();
        }

        Set<Hobby> semanticMatches = findHobbyMatchesBySimilarity(allByLocation, testResults);
        if (semanticMatches != null) {
            return semanticMatches;
        }

        // Embeddings unavailable (no API key configured, or the provider call
        // failed) - fall back to the original random-sample + category-match logic.
        return findHobbyMatchesByCategory(allByLocation, testResults);
    }

    /**
     * Ranks hobbies by cosine similarity between the user's test-result profile
     * and each hobby's stored embedding. Returns null (rather than an empty set)
     * when embeddings aren't available, so the caller can distinguish "no AI
     * match possible" from "AI matched nothing".
     */
    private Set<Hobby> findHobbyMatchesBySimilarity(List<Hobby> allByLocation, Test testResults) {
        double[] profileEmbedding = this.embeddingService.embed(buildProfileText(testResults));
        if (profileEmbedding == null) {
            return null;
        }

        List<Map.Entry<Hobby, double[]>> withEmbeddings = allByLocation.stream()
                .map(hobby -> Map.entry(hobby, deserializeEmbedding(hobby.getEmbedding())))
                .filter(entry -> entry.getValue() != null)
                .collect(Collectors.toList());

        if (withEmbeddings.isEmpty()) {
            return null;
        }

        return withEmbeddings.stream()
                .sorted(Comparator.<Map.Entry<Hobby, double[]>>comparingDouble(
                        entry -> cosineSimilarity(profileEmbedding, entry.getValue())).reversed())
                .limit(MAX_MATCHES)
                .map(Map.Entry::getKey)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Set<Hobby> findHobbyMatchesByCategory(List<Hobby> allByLocation, Test testResults) {
        Set<Hobby> hobby_matches = new HashSet<>();
        boolean isAdded = false;
        Random rand = new Random();
        List<CategoryNameEnum> testCategoryResults = List.of(
                testResults.getCategoryOne(), testResults.getCategoryTwo(), testResults.getCategoryThree(),
                testResults.getCategoryFour(), testResults.getCategoryFive(), testResults.getCategorySix());

        for (int i = 0; i < MAX_MATCHES; i++) {
            int randomIndex = rand.nextInt(allByLocation.size());
            Hobby randomHobby = allByLocation.get(randomIndex);
            if (hobby_matches.contains(randomHobby)) {
                continue;
            }
            for (CategoryNameEnum testCategory : testCategoryResults) {
                if (randomHobby.getCategory().getName().equals(testCategory)) {
                    hobby_matches.add(randomHobby);
                    isAdded = true;
                }
                if (isAdded) {
                    isAdded = false;
                    break;
                }
            }
        }
        return hobby_matches;
    }

    private String buildProfileText(Test testResults) {
        return "Looking for hobbies related to: " +
                testResults.getCategoryOne() + ", " + testResults.getCategoryTwo() + ", " +
                testResults.getCategoryThree() + ", " + testResults.getCategoryFour() + ", " +
                testResults.getCategoryFive() + ", " + testResults.getCategorySix() +
                ". Location: " + testResults.getLocation() + ".";
    }

    private double cosineSimilarity(double[] a, double[] b) {
        if (a.length != b.length) {
            return -1;
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        if (normA == 0 || normB == 0) {
            return 0;
        }
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }

    private void applyEmbedding(Hobby hobby) {
        double[] vector = this.embeddingService.embed(buildHobbyText(hobby));
        hobby.setEmbedding(serializeEmbedding(vector));
    }

    private String buildHobbyText(Hobby hobby) {
        StringBuilder sb = new StringBuilder();
        if (hobby.getName() != null) sb.append(hobby.getName()).append(". ");
        if (hobby.getSlogan() != null) sb.append(hobby.getSlogan()).append(". ");
        if (hobby.getIntro() != null) sb.append(hobby.getIntro()).append(". ");
        if (hobby.getDescription() != null) sb.append(hobby.getDescription()).append(". ");
        if (hobby.getCategory() != null && hobby.getCategory().getName() != null) {
            sb.append("Category: ").append(hobby.getCategory().getName()).append(".");
        }
        return sb.toString();
    }

    private String serializeEmbedding(double[] vector) {
        if (vector == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(vector);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private double[] deserializeEmbedding(String json) {
        if (json == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(json, double[].class);
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public boolean saveHobbyForClient(Hobby hobby, String username) {
        AppClient currentUserAppClient = this.userService.findAppClientByUsername(username);
        Optional<Hobby> hobbyById = this.hobbyRepository.findById(hobby.getId());
        List<Hobby> saved_hobbies = currentUserAppClient.getSaved_hobbies();
        if (hobbyById.isPresent() && !(saved_hobbies.contains(hobbyById.get()))) {
            saved_hobbies.add(hobbyById.get());
            return true;
        }
        return false;
    }

    @Override
    public boolean removeHobbyForClient(Hobby hobby, String username) {
        AppClient currentUserAppClient = this.userService.findAppClientByUsername(username);
        Optional<Hobby> hobbyById = this.hobbyRepository.findById(hobby.getId());
        if (currentUserAppClient != null) {
            hobbyById.ifPresent(value -> currentUserAppClient.getSaved_hobbies().remove(value));
            return true;
        }
        return false;
    }

    @Override
    public boolean isHobbySaved(Long hobbyId, String username) {
        Optional<Hobby> byId = this.hobbyRepository.findById(hobbyId);
        if (byId.isPresent()) {
            AppClient currentUserAppClient = this.userService.findAppClientByUsername(username);
            return currentUserAppClient.getSaved_hobbies().contains(byId.get());
        }
        return false;
    }

    @Override
    public List<Hobby> findSavedHobbies(AppClient currentAppClient) {
        return currentAppClient.getSaved_hobbies();
    }

    @Override
    public Set<Hobby> getAllHobbiesForBusiness(String username) {
        return this.hobbyRepository.findAllByCreator(username);
    }

    @Override
    public Set<Hobby> getAllHobbieMatchesForClient(String username) {
        AppClient currentUserAppClient = this.userService.findAppClientByUsername(username);
        return currentUserAppClient.getHobby_matches();
    }

    @Override
    public void createHobby(Hobby offer) {
        applyEmbedding(offer);
        this.hobbyRepository.save(offer);
    }

    @Override
    public List<Hobby> seedHobbies() {
        List<Hobby> seeded = new ArrayList<>();
        if (hobbyRepository.count() == 0) {
            BusinessOwner business = this.userService.findBusinessByUsername("business");
            Set<Hobby> hobbyOffers = business.getHobby_offers();

            Object[][] data = {
                    {"Pottery Workshop", "Shape your creativity", CategoryNameEnum.CREATIVE, LocationEnum.ZURICH, new BigDecimal("45.00"), "Learn to throw and shape clay on the wheel in a beginner-friendly pottery class.", "A hands-on introduction to ceramics, perfect for first-timers and art lovers alike."},
                    {"Sunrise Yoga", "Breathe. Stretch. Repeat.", CategoryNameEnum.RELAX, LocationEnum.ZURICH, new BigDecimal("20.00"), "Start your day with a calming yoga session by the lake.", "Gentle flows suitable for all levels, led by a certified instructor."},
                    {"Indoor Rock Climbing", "Reach new heights", CategoryNameEnum.ACTIVE, LocationEnum.BERN, new BigDecimal("30.00"), "Climb your way up our indoor bouldering walls.", "Includes gear rental and a short safety briefing for beginners."},
                    {"Board Game Night", "Roll the dice, make friends", CategoryNameEnum.SOCIAL, LocationEnum.BERN, new BigDecimal("15.00"), "Join a weekly board game meetup with dozens of titles to choose from.", "A relaxed social evening for strategy fans and casual players alike."},
                    {"Astronomy Night", "Explore the stars", CategoryNameEnum.INTELLECTUAL, LocationEnum.LUZERN, new BigDecimal("25.00"), "Observe planets and constellations through professional telescopes.", "Guided by a local astronomy club, great for curious minds of all ages."},
                    {"Salsa Dancing", "Move to the rhythm", CategoryNameEnum.FUN, LocationEnum.LUZERN, new BigDecimal("18.00"), "Learn the basics of salsa in a fun, energetic group class.", "No partner or experience required, just bring comfortable shoes."},
                    {"Photography Walk", "Capture the moment", CategoryNameEnum.CREATIVE, LocationEnum.ZUG, new BigDecimal("22.00"), "Explore the old town while learning composition and lighting techniques.", "Suitable for beginners with any camera, including smartphones."},
                    {"Chess Club", "Outsmart your opponent", CategoryNameEnum.INTELLECTUAL, LocationEnum.ZUG, new BigDecimal("10.00"), "Weekly chess meetups for players of all skill levels.", "Friendly tournaments and coaching sessions available."},
                    {"Trail Running Group", "Run further together", CategoryNameEnum.ACTIVE, LocationEnum.ZURICH, new BigDecimal("12.00"), "Join a group trail run through the forest trails.", "Routes available for both beginners and experienced runners."},
                    {"Watercolor Painting", "Let your imagination flow", CategoryNameEnum.CREATIVE, LocationEnum.BERN, new BigDecimal("28.00"), "A relaxing watercolor painting class for all experience levels.", "All materials are provided, just bring your creativity."},
            };

            for (Object[] row : data) {
                Hobby hobby = new Hobby();
                hobby.setName((String) row[0]);
                hobby.setSlogan((String) row[1]);
                hobby.setCategory(this.categoryService.findByName((CategoryNameEnum) row[2]));
                hobby.setLocation(this.locationService.getLocationByName((LocationEnum) row[3]));
                hobby.setPrice((BigDecimal) row[4]);
                hobby.setIntro((String) row[5]);
                hobby.setDescription((String) row[6]);
                hobby.setCreator(business.getUsername());
                hobby.setContactInfo("business@example.com");
                hobby.setProfileImgUrl("https://picsum.photos/seed/" + ((String) row[0]).replace(" ", "") + "/600/400");

                applyEmbedding(hobby);
                this.hobbyRepository.save(hobby);
                hobbyOffers.add(hobby);
                seeded.add(hobby);
            }

            business.setHobby_offers(hobbyOffers);
            this.userService.saveUpdatedUser(business);
        }
        return seeded;
    }

}

