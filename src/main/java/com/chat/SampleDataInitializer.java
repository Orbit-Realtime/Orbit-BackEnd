//package com.chat;
//
//import com.chat.entity.*;
//import com.chat.repository.*;
//import com.chat.service.utils.PasswordEncoder;
//import jakarta.annotation.PostConstruct;
//import jakarta.persistence.EntityManager;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Profile;
//import org.springframework.stereotype.Component;
//import org.springframework.transaction.annotation.Propagation;
//import org.springframework.transaction.annotation.Transactional;
//
//import java.util.*;
//
///**
// * 부하 테스트용 샘플 데이터 생성기
// *
// * 생성되는 데이터:
// * - 사용자: 1000명 (user1 ~ user1000, 비밀번호는 username과 동일)
// * - 1:1 채팅방: 200개 (각 채팅방당 30~80개 메시지)
// * - 그룹 채팅방: 100개 (3~8명 참여, 각 채팅방당 50~100개 메시지)
// * - 총 메시지: 약 18,500개
// * - 읽음 상태: 랜덤하게 설정 (70% 확률)
// * - 총 레코드: 약 84,000개
// *
// * 트랜잭션 분리: 메모리 효율을 위해 사용자/채팅방/메시지 생성을 별도 트랜잭션으로 처리
// * 활성화: --spring.profiles.active=dev 또는 local
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//@Profile({"dev", "local"})
//public class SampleDataInitializer {
//
//    private final SampleDataService sampleDataService;
//
//    @PostConstruct
//    public void init() {
//        sampleDataService.generateSampleData();
//    }
//
//    @Component
//    @RequiredArgsConstructor
//    static class SampleDataService {
//
//        private final MemberRepository memberRepository;
//        private final SpaceRepository spaceRepository;
//        private final SpaceMemberRepository spaceMemberRepository;
//        private final MessageRepository messageRepository;
//        private final PasswordEncoder passwordEncoder;
//        private final EntityManager entityManager;
//
//        private final Random random = new Random(42); // 재현 가능한 랜덤
//        private static final int BATCH_SIZE = 100; // 배치 처리 단위
//
//        // 실제 채팅에서 사용될 법한 메시지 샘플
//        private static final String[] SAMPLE_MESSAGES = {
//            // 인사
//            "안녕하세요!", "안녕~", "하이", "ㅎㅇ", "굿모닝!", "좋은 아침이에요",
//            // 일상 대화
//            "오늘 뭐해?", "밥 먹었어?", "점심 뭐 먹을까", "오늘 날씨 좋다", "퇴근하고 뭐해?",
//            "주말에 시간 돼?", "오늘 회의 몇 시야?", "그거 다 했어?", "조금만 기다려줘",
//            // 반응
//            "ㅋㅋㅋㅋ", "ㅎㅎ", "ㄹㅇ", "진짜?", "대박", "헐", "와", "오", "굿",
//            "알겠어", "응응", "ㅇㅇ", "그래그래", "나도", "아 그렇구나",
//            // 업무 관련
//            "확인했습니다", "네 알겠습니다", "수고하셨습니다", "감사합니다",
//            "잠시만요", "바로 확인해볼게요", "공유 부탁드려요", "회의실 예약했어요",
//            // 질문
//            "언제 되나요?", "어디서 만날까?", "그거 어떻게 해?", "왜?", "진행 상황이 어때요?",
//            // 이모티콘/표현
//            "👍", "😊", "😂", "🙏", "💪", "🎉", "❤️", "👏",
//            // 긴 메시지
//            "내일 오전 10시에 회의실에서 만나요. 준비물 있으면 미리 말씀해주세요.",
//            "지난번에 얘기했던 그 건 어떻게 됐어? 진행 상황 공유해줄 수 있어?",
//            "이번 주 금요일까지 마무리해야 하는데 가능할까요? 어려우면 말씀해주세요.",
//        };
//
//        // 채팅방 이름 템플릿
//        private static final String[] ROOM_NAME_TEMPLATES = {
//            "프로젝트 %s팀", "%s 스터디", "동아리 %s", "%s 모임",
//            "팀 %s", "%s 그룹", "친구들 %s", "%s 회의"
//        };
//
//        private static final String[] ROOM_SUFFIXES = {
//            "A", "B", "알파", "베타", "1", "2", "개발", "기획", "디자인"
//        };
//
//        @Transactional
//        public void generateSampleData() {
//            long startTime = System.currentTimeMillis();
//            log.info("========================================");
//            log.info("샘플 데이터 생성 시작");
//            log.info("========================================");
//
//            // 1. 사용자 생성 (별도 트랜잭션)
//            List<Member> members = createMembersWithTransaction(1000);
//            log.info("사용자 {}명 생성 완료", members.size());
//
//            // 2. 1:1 채팅방 생성 (별도 트랜잭션)
//            List<Space> directRooms = createDirectSpacesWithTransaction(members, 200);
//            log.info("1:1 채팅방 {}개 생성 완료", directRooms.size());
//
//            // 3. 그룹 채팅방 생성 (별도 트랜잭션)
//            List<Space> groupRooms = createGroupSpacesWithTransaction(members, 100);
//            log.info("그룹 채팅방 {}개 생성 완료", groupRooms.size());
//
//            // 4. 1:1 채팅 메시지 생성 (별도 트랜잭션으로 배치 처리)
//            int directMessages = createMessagesForSpacesBatch(directRooms, 30, 80, "1:1");
//            log.info("1:1 채팅방 메시지 {}개 생성 완료", directMessages);
//
//            // 5. 그룹 채팅 메시지 생성 (별도 트랜잭션으로 배치 처리)
//            int groupMessages = createMessagesForSpacesBatch(groupRooms, 50, 100, "그룹");
//            log.info("그룹 채팅방 메시지 {}개 생성 완료", groupMessages);
//
//            int totalMessages = directMessages + groupMessages;
//            long endTime = System.currentTimeMillis();
//            long duration = (endTime - startTime) / 1000;
//
//            log.info("========================================");
//            log.info("샘플 데이터 생성 완료 (소요 시간: {}초)", duration);
//            log.info("========================================");
//            printSummary(members, directRooms.size(), groupRooms.size(), totalMessages);
//        }
//
//        @Transactional(propagation = Propagation.REQUIRES_NEW)
//        public List<Member> createMembersWithTransaction(int count) {
//            return createMembers(count);
//        }
//
//        private List<Member> createMembers(int count) {
//            List<Member> members = new ArrayList<>();
//
//            for (int i = 1; i <= count; i++) {
//                String username = "user" + i;
//                String nickname = "사용자" + i;
//                String encodedPassword = passwordEncoder.encode(username);
//
//                Member member = Member.of(username, encodedPassword, nickname);
//                members.add(memberRepository.save(member));
//
//                if (i % BATCH_SIZE == 0) {
//                    memberRepository.flush();
//                    entityManager.clear();
//                    log.info("사용자 {}/{}명 생성 중...", i, count);
//                }
//            }
//
//            memberRepository.flush();
//            entityManager.clear();
//            return members;
//        }
//
//        @Transactional(propagation = Propagation.REQUIRES_NEW)
//        public List<Space> createDirectSpacesWithTransaction(List<Member> members, int count) {
//            return createDirectSpaces(members, count);
//        }
//
//        private List<Space> createDirectSpaces(List<Member> members, int count) {
//            List<Space> rooms = new ArrayList<>();
//            Set<String> existingPairs = new HashSet<>();
//
//            int attempts = 0;
//            while (rooms.size() < count && attempts < count * 3) {
//                attempts++;
//
//                Member member1 = members.get(random.nextInt(members.size()));
//                Member member2 = members.get(random.nextInt(members.size()));
//
//                if (member1.equals(member2)) continue;
//
//                String pairKey = Math.min(member1.getId(), member2.getId()) + "-"
//                               + Math.max(member1.getId(), member2.getId());
//                if (existingPairs.contains(pairKey)) continue;
//                existingPairs.add(pairKey);
//
//                String title = member1.getNickname() + ", " + member2.getNickname();
//                Space room = spaceRepository.save(Space.of(title));
//
//                addSpaceMember(room, member1);
//                addSpaceMember(room, member2);
//
//                rooms.add(room);
//
//                if (rooms.size() % 50 == 0) {
//                    spaceRepository.flush();
//                    entityManager.clear();
//                    log.info("1:1 채팅방 {}/{}개 생성 중...", rooms.size(), count);
//                }
//            }
//
//            spaceRepository.flush();
//            entityManager.clear();
//            return rooms;
//        }
//
//        @Transactional(propagation = Propagation.REQUIRES_NEW)
//        public List<Space> createGroupSpacesWithTransaction(List<Member> members, int count) {
//            return createGroupSpaces(members, count);
//        }
//
//        private List<Space> createGroupSpaces(List<Member> members, int count) {
//            List<Space> rooms = new ArrayList<>();
//
//            for (int i = 0; i < count; i++) {
//                int participantCount = 3 + random.nextInt(6);
//                List<Member> shuffled = new ArrayList<>(members);
//                Collections.shuffle(shuffled, random);
//                List<Member> participants = shuffled.subList(0, Math.min(participantCount, members.size()));
//
//                String template = ROOM_NAME_TEMPLATES[random.nextInt(ROOM_NAME_TEMPLATES.length)];
//                String suffix = ROOM_SUFFIXES[random.nextInt(ROOM_SUFFIXES.length)];
//                String title = String.format(template, suffix);
//
//                Space room = spaceRepository.save(Space.of(title));
//
//                for (Member participant : participants) {
//                    addSpaceMember(room, participant);
//                }
//
//                rooms.add(room);
//
//                if ((i + 1) % 50 == 0) {
//                    spaceRepository.flush();
//                    entityManager.clear();
//                    log.info("그룹 채팅방 {}/{}개 생성 중...", i + 1, count);
//                }
//            }
//
//            spaceRepository.flush();
//            entityManager.clear();
//            return rooms;
//        }
//
//        private void addSpaceMember(Space space, Member member) {
//            Member managedMember = entityManager.getReference(Member.class, member.getId());
//            SpaceMember spaceMember = SpaceMember.of(managedMember, space);
//            spaceMemberRepository.save(spaceMember);
//        }
//
//        private int createMessagesForSpacesBatch(List<Space> rooms, int minMessages, int maxMessages, String roomType) {
//            int totalMessages = 0;
//            int roomCount = rooms.size();
//            int processedRooms = 0;
//
//            for (int i = 0; i < rooms.size(); i += 10) {
//                int endIndex = Math.min(i + 10, rooms.size());
//                List<Space> batch = rooms.subList(i, endIndex);
//
//                int batchMessages = createMessagesForSpaceBatchWithTransaction(batch, minMessages, maxMessages);
//                totalMessages += batchMessages;
//                processedRooms += batch.size();
//
//                log.info("{} 채팅방 메시지 생성 진행 중... ({}/{})", roomType, processedRooms, roomCount);
//            }
//
//            return totalMessages;
//        }
//
//        @Transactional(propagation = Propagation.REQUIRES_NEW)
//        public int createMessagesForSpaceBatchWithTransaction(List<Space> rooms, int minMessages, int maxMessages) {
//            int totalMessages = 0;
//            for (Space room : rooms) {
//                totalMessages += createMessagesForSpace(room, minMessages, maxMessages);
//            }
//            return totalMessages;
//        }
//
//        private int createMessagesForSpace(Space room, int minMessages, int maxMessages) {
//            List<SpaceMember> spaceMembers = spaceMemberRepository.findAllFetchMemberBy(room.getId());
//            if (spaceMembers.isEmpty()) return 0;
//
//            List<Member> roomMembers = spaceMembers.stream()
//                    .map(SpaceMember::getMember)
//                    .toList();
//
//            int messageCount = minMessages + random.nextInt(maxMessages - minMessages + 1);
//            List<Message> messages = new ArrayList<>();
//
//            Space managedSpace = entityManager.getReference(Space.class, room.getId());
//
//            for (int i = 0; i < messageCount; i++) {
//                Member sender = roomMembers.get(random.nextInt(roomMembers.size()));
//                String text = SAMPLE_MESSAGES[random.nextInt(SAMPLE_MESSAGES.length)];
//                messages.add(new Message(text, sender, managedSpace));
//            }
//
//            List<Message> savedMessages = messageRepository.saveAll(messages);
//            messageRepository.flush();
//
//            for (Member member : roomMembers) {
//                int readCount = (int)(savedMessages.size() * (0.5 + random.nextDouble() * 0.5));
//                if (readCount > 0) {
//                    Message lastRead = savedMessages.get(readCount - 1);
//                    spaceMemberRepository.updateLastReadMessageId(
//                            member.getId(), managedSpace.getId(), lastRead.getId());
//                }
//            }
//            entityManager.clear();
//
//            return messageCount;
//        }
//
//        private void printSummary(List<Member> members, int directRoomCount, int groupRoomCount, int totalMessages) {
//            log.info("");
//            log.info("================== 생성 데이터 요약 ==================");
//            log.info("사용자 수:           {}명", members.size());
//            log.info("1:1 채팅방:          {}개 (각 30~80개 메시지)", directRoomCount);
//            log.info("그룹 채팅방:         {}개 (각 50~100개 메시지)", groupRoomCount);
//            log.info("총 채팅방:           {}개", directRoomCount + groupRoomCount);
//            log.info("총 메시지:           약 {}개", totalMessages);
//
//            int totalRecords = members.size() + directRoomCount + groupRoomCount
//                             + (directRoomCount * 2) + (int)(groupRoomCount * 5.5)
//                             + totalMessages;
//            log.info("총 레코드 수:        약 {}개", totalRecords);
//            log.info("====================================================");
//            log.info("");
//            log.info("============ 테스트 계정 샘플 (처음 5개) ============");
//            log.info(String.format("| %-10s | %-10s | %-12s |", "Username", "Password", "Nickname"));
//            log.info("|------------|------------|--------------|");
//            for (Member member : members.subList(0, Math.min(5, members.size()))) {
//                log.info(String.format("| %-10s | %-10s | %-12s |",
//                    member.getUsername(),
//                    member.getUsername(),
//                    member.getNickname()));
//            }
//            if (members.size() > 5) {
//                log.info("|    ...     |    ...     |     ...      |");
//                Member last = members.get(members.size() - 1);
//                log.info(String.format("| %-10s | %-10s | %-12s |",
//                    last.getUsername(), last.getUsername(), last.getNickname()));
//            }
//            log.info("====================================================");
//            log.info("💡 모든 사용자의 비밀번호는 username과 동일합니다");
//            log.info("   예: user1 / user1, user100 / user100");
//        }
//    }
//}
