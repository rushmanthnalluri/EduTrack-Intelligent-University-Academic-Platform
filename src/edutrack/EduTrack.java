package edutrack;

import java.util.Scanner;

import edutrack.api.ApiServer;
import edutrack.data.DataStore;
import edutrack.features.ExamAnalytics;
import edutrack.features.ReportGenerator;
import edutrack.modules.M1StringAlgorithms;
import edutrack.modules.M2SuffixStructures;
import edutrack.modules.M3DynamicProgramming;
import edutrack.modules.M4NetworkFlow;
import edutrack.modules.M5NPCompleteness;
import edutrack.modules.M6RandomizedParallel;
import edutrack.search.SearchService;

public class EduTrack {

    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);
        int choice;

        System.out.println("Loading academic data ...");
        DataStore dataStore = new DataStore();
        System.out.println("Students   : " + dataStore.students().size());
        System.out.println("Faculty    : " + dataStore.faculty().size());
        System.out.println("Courses    : " + dataStore.courses().size());
        System.out.println("Assignments: " + dataStore.assignments().size());
        System.out.println("Resources  : " + dataStore.resources().size());
        System.out.println("Exam records: " + dataStore.examRecords().size());
        System.out.println("Activities : " + dataStore.activityStream().size());

        do {
            System.out.println("\n========================================");
            System.out.println("            EDUTRACK");
            System.out.println(" Intelligent University Academic Platform");
            System.out.println("========================================");
            System.out.println("1. Academic Search (String Algorithms)");
            System.out.println("2. Document Indexing & Similarity (Suffix Structures)");
            System.out.println("3. Query Correction & Optimization (Advanced DP)");
            System.out.println("4. Resource Allocation (Network Flow)");
            System.out.println("5. Exam Scheduling (NP-Completeness & Approximation)");
            System.out.println("6. Ranking & Stream Sampling (Randomized & Parallel)");
            System.out.println("7. Smart Search (all records)");
            System.out.println("8. Exam Analytics");
            System.out.println("9. Reports & Transcripts");
            System.out.println("10. REST API Server");
            System.out.println("11. About EduTrack");
            System.out.println("12. Exit");
            System.out.println("========================================");
            System.out.print("Enter your choice : ");

            choice = readInt(sc);

            switch (choice) {

                case 1:
                    M1StringAlgorithms.run(sc, dataStore);
                    break;

                case 2:
                    M2SuffixStructures.run(sc, dataStore);
                    break;

                case 3:
                    M3DynamicProgramming.run(sc, dataStore);
                    break;

                case 4:
                    M4NetworkFlow.run(sc, dataStore);
                    break;

                case 5:
                    M5NPCompleteness.run(sc, dataStore);
                    break;

                case 6:
                    M6RandomizedParallel.run(sc, dataStore);
                    break;

                case 7:
                    SearchService.run(sc, dataStore);
                    break;

                case 8:
                    ExamAnalytics.run(sc, dataStore);
                    break;

                case 9:
                    ReportGenerator.run(sc, dataStore);
                    break;

                case 10:
                    ApiServer.run(sc, dataStore);
                    break;

                case 11:
                    aboutProject();
                    break;

                case 12:
                    System.out.println("\nThank you for using EduTrack.");
                    break;

                default:
                    System.out.println("\nInvalid Choice!");
            }

        } while (choice != 12);

        sc.close();
    }

    static void aboutProject() {

        System.out.println("\n========== ABOUT EDUTRACK ==========");
        System.out.println("Project Name : EduTrack - Intelligent University Academic Platform");
        System.out.println("Course       : DSA-3");
        System.out.println("Description  :");
        System.out.println("EduTrack models a university academic platform that manages student and faculty records, "
                + "courses, assignments, examination records, learning resources, academic schedules and "
                + "continuous student activity data. On top of the records layer (smart search, exam analytics, "
                + "activity analytics) it demonstrates six DSA modules: string algorithms (M1), "
                + "suffix structures for document similarity (M2), advanced dynamic programming (M3), "
                + "network flow for resource allocation (M4), NP-completeness and approximation for exam "
                + "scheduling (M5), and randomized & parallel algorithms for ranking and streaming data (M6). "
                + "A Swing GUI is available as edutrack.gui.EduTrackGUI.");
        System.out.println("====================================");
    }

    private static int readInt(Scanner sc) {
        while (!sc.hasNextInt()) {
            sc.next();
            System.out.print("Please enter a valid number : ");
        }
        int value = sc.nextInt();
        sc.nextLine();
        return value;
    }
}
