package com.chat;

import com.chat.entity.*;
import com.chat.repository.*;
import com.chat.service.utils.PasswordEncoder;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 부하 테스트용 샘플 데이터 생성기
 *
 * 생성되는 데이터:
 * - 사용자: 1000명 (user1 ~ user1000, 비밀번호는 username과 동일)
 * - 1:1 채팅방: 200개 (각 채팅방당 30~80개 메시지)
 * - 그룹 채팅방: 100개 (3~8명 참여, 각 채팅방당 50~100개 메시지)
 * - 총 메시지: 약 18,500개
 * - 읽음 상태: 랜덤하게 설정 (70% 확률)
 * - 총 레코드: 약 84,000개
 *
 * 트랜잭션 분리: 메모리 효율을 위해 사용자/채팅방/메시지 생성을 별도 트랜잭션으로 처리
 * 활성화: --spring.profiles.active=dev 또는 local
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Profile({"dev", "local"})
public class SampleDataInitializer {

    private final SampleDataService sampleDataService;

    @PostConstruct
    public void init() {
        sampleDataService.generateSampleData();
    }

    @Component
    @RequiredArgsConstructor
    static class SampleDataService {

        private final MemberRepository memberRepository;
        private final ChatRoomRepository chatRoomRepository;
        private final ChatRoomParticipantRepository participantRepository;
        private final ChatRepository chatRepository;
        private final PasswordEncoder passwordEncoder;
        private final EntityManager entityManager;

        private final Random random = new Random(42); // 재현 가능한 랜덤
        private static final int BATCH_SIZE = 100; // 배치 처리 단위

        // 실제 채팅에서 사용될 법한 메시지 샘플
        private static final String[] SAMPLE_MESSAGES = {
            // 인사
            "안녕하세요!", "안녕~", "하이", "ㅎㅇ", "굿모닝!", "좋은 아침이에요",
            // 일상 대화
            "오늘 뭐해?", "밥 먹었어?", "점심 뭐 먹을까", "오늘 날씨 좋다", "퇴근하고 뭐해?",
            "주말에 시간 돼?", "오늘 회의 몇 시야?", "그거 다 했어?", "조금만 기다려줘",
            // 반응
            "ㅋㅋㅋㅋ", "ㅎㅎ", "ㄹㅇ", "진짜?", "대박", "헐", "와", "오", "굿",
            "알겠어", "응응", "ㅇㅇ", "그래그래", "나도", "아 그렇구나",
            // 업무 관련
            "확인했습니다", "네 알겠습니다", "수고하셨습니다", "감사합니다",
            "잠시만요", "바로 확인해볼게요", "공유 부탁드려요", "회의실 예약했어요",
            // 질문
            "언제 되나요?", "어디서 만날까?", "그거 어떻게 해?", "왜?", "진행 상황이 어때요?",
            // 이모티콘/표현
            "👍", "😊", "😂", "🙏", "💪", "🎉", "❤️", "👏",
            // 긴 메시지
            "내일 오전 10시에 회의실에서 만나요. 준비물 있으면 미리 말씀해주세요.",
            "지난번에 얘기했던 그 건 어떻게 됐어? 진행 상황 공유해줄 수 있어?",
            "이번 주 금요일까지 마무리해야 하는데 가능할까요? 어려우면 말씀해주세요.",
        };

        // 채팅방 이름 템플릿
        private static final String[] ROOM_NAME_TEMPLATES = {
            "프로젝트 %s팀", "%s 스터디", "동아리 %s", "%s 모임",
            "팀 %s", "%s 그룹", "친구들 %s", "%s 회의"
        };

        private static final String[] ROOM_SUFFIXES = {
            "A", "B", "알파", "베타", "1", "2", "개발", "기획", "디자인"
        };

        @Transactional
        public void generateSampleData() {
            long startTime = System.currentTimeMillis();
            log.info("========================================");
            log.info("샘플 데이터 생성 시작");
            log.info("========================================");

            // 1. 사용자 생성 (별도 트랜잭션)
            List<Member> members = createMembersWithTransaction(1000);
            log.info("사용자 {}명 생성 완료", members.size());

            // 2. 1:1 채팅방 생성 (별도 트랜잭션)
            List<ChatRoom> directRooms = createDirectChatRoomsWithTransaction(members, 200);
            log.info("1:1 채팅방 {}개 생성 완료", directRooms.size());

            // 3. 그룹 채팅방 생성 (별도 트랜잭션)
            List<ChatRoom> groupRooms = createGroupChatRoomsWithTransaction(members, 100);
            log.info("그룹 채팅방 {}개 생성 완료", groupRooms.size());

            // 4. 1:1 채팅 메시지 생성 (별도 트랜잭션으로 배치 처리)
            int directMessages = createMessagesForRoomsBatch(directRooms, 30, 80, "1:1");
            log.info("1:1 채팅방 메시지 {}개 생성 완료", directMessages);

            // 5. 그룹 채팅 메시지 생성 (별도 트랜잭션으로 배치 처리)
            int groupMessages = createMessagesForRoomsBatch(groupRooms, 50, 100, "그룹");
            log.info("그룹 채팅방 메시지 {}개 생성 완료", groupMessages);

            int totalMessages = directMessages + groupMessages;
            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;

            log.info("========================================");
            log.info("샘플 데이터 생성 완료 (소요 시간: {}초)", duration);
            log.info("========================================");
            printSummary(members, directRooms.size(), groupRooms.size(), totalMessages);
        }

        /**
         * 트랜잭션 래퍼: 사용자 생성
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public List<Member> createMembersWithTransaction(int count) {
            return createMembers(count);
        }

        /**
         * 테스트 사용자 생성 (배치 처리)
         * username: user1 ~ user{count}
         * password: username과 동일 (user1의 비밀번호는 user1)
         */
        private List<Member> createMembers(int count) {
            List<Member> members = new ArrayList<>();

            for (int i = 1; i <= count; i++) {
                String username = "user" + i;
                String nickname = "사용자" + i;
                String encodedPassword = passwordEncoder.encode(username);

                Member member = Member.of(username, encodedPassword, nickname);
                members.add(memberRepository.save(member));

                // 배치 단위로 flush & clear
                if (i % BATCH_SIZE == 0) {
                    memberRepository.flush();
                    entityManager.clear();
                    log.info("사용자 {}/{}명 생성 중...", i, count);
                }
            }

            memberRepository.flush();
            entityManager.clear();
            return members;
        }

        /**
         * 트랜잭션 래퍼: 1:1 채팅방 생성
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public List<ChatRoom> createDirectChatRoomsWithTransaction(List<Member> members, int count) {
            return createDirectChatRooms(members, count);
        }

        /**
         * 1:1 채팅방 생성 (배치 처리)
         * 랜덤하게 두 사용자를 선택하여 1:1 채팅방 생성
         */
        private List<ChatRoom> createDirectChatRooms(List<Member> members, int count) {
            List<ChatRoom> rooms = new ArrayList<>();
            Set<String> existingPairs = new HashSet<>();

            int attempts = 0;
            while (rooms.size() < count && attempts < count * 3) {
                attempts++;

                // 랜덤하게 두 사용자 선택
                Member member1 = members.get(random.nextInt(members.size()));
                Member member2 = members.get(random.nextInt(members.size()));

                if (member1.equals(member2)) continue;

                // 중복 방지
                String pairKey = Math.min(member1.getId(), member2.getId()) + "-"
                               + Math.max(member1.getId(), member2.getId());
                if (existingPairs.contains(pairKey)) continue;
                existingPairs.add(pairKey);

                // 채팅방 생성
                String title = member1.getNickname() + ", " + member2.getNickname();
                ChatRoom room = chatRoomRepository.save(ChatRoom.of(title));

                // 참여자 추가
                addParticipant(room, member1);
                addParticipant(room, member2);

                rooms.add(room);

                // 배치 단위로 flush & clear
                if (rooms.size() % 50 == 0) {
                    chatRoomRepository.flush();
                    entityManager.clear();
                    log.info("1:1 채팅방 {}/{}개 생성 중...", rooms.size(), count);
                }
            }

            chatRoomRepository.flush();
            entityManager.clear();
            return rooms;
        }

        /**
         * 트랜잭션 래퍼: 그룹 채팅방 생성
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public List<ChatRoom> createGroupChatRoomsWithTransaction(List<Member> members, int count) {
            return createGroupChatRooms(members, count);
        }

        /**
         * 그룹 채팅방 생성 (배치 처리)
         * 3~8명의 랜덤 참여자로 그룹 채팅방 생성
         */
        private List<ChatRoom> createGroupChatRooms(List<Member> members, int count) {
            List<ChatRoom> rooms = new ArrayList<>();

            for (int i = 0; i < count; i++) {
                // 3~8명 랜덤 참여
                int participantCount = 3 + random.nextInt(6);
                List<Member> shuffled = new ArrayList<>(members);
                Collections.shuffle(shuffled, random);
                List<Member> participants = shuffled.subList(0, Math.min(participantCount, members.size()));

                // 채팅방 이름 생성
                String template = ROOM_NAME_TEMPLATES[random.nextInt(ROOM_NAME_TEMPLATES.length)];
                String suffix = ROOM_SUFFIXES[random.nextInt(ROOM_SUFFIXES.length)];
                String title = String.format(template, suffix);

                ChatRoom room = chatRoomRepository.save(ChatRoom.of(title));

                // 참여자 추가
                for (Member participant : participants) {
                    addParticipant(room, participant);
                }

                rooms.add(room);

                // 배치 단위로 flush & clear
                if ((i + 1) % 50 == 0) {
                    chatRoomRepository.flush();
                    entityManager.clear();
                    log.info("그룹 채팅방 {}/{}개 생성 중...", i + 1, count);
                }
            }

            chatRoomRepository.flush();
            entityManager.clear();
            return rooms;
        }

        /**
         * 채팅방에 참여자 추가
         * member는 다른 트랜잭션에서 생성된 detached 엔티티일 수 있으므로
         * getReference()로 현재 트랜잭션의 프록시 참조를 사용
         */
        private void addParticipant(ChatRoom room, Member member) {
            Member managedMember = entityManager.getReference(Member.class, member.getId());
            ChatRoomParticipant participant = ChatRoomParticipant.builder()
                    .chatRoom(room)
                    .member(managedMember)
                    .build();
            participantRepository.save(participant);
        }

        /**
         * 여러 채팅방의 메시지를 배치로 생성 (트랜잭션 분리)
         */
        private int createMessagesForRoomsBatch(List<ChatRoom> rooms, int minMessages, int maxMessages, String roomType) {
            int totalMessages = 0;
            int roomCount = rooms.size();
            int processedRooms = 0;

            // 10개 채팅방마다 별도 트랜잭션 처리
            for (int i = 0; i < rooms.size(); i += 10) {
                int endIndex = Math.min(i + 10, rooms.size());
                List<ChatRoom> batch = rooms.subList(i, endIndex);

                int batchMessages = createChatsForRoomBatchWithTransaction(batch, minMessages, maxMessages);
                totalMessages += batchMessages;
                processedRooms += batch.size();

                log.info("{} 채팅방 메시지 생성 진행 중... ({}/{})", roomType, processedRooms, roomCount);
            }

            return totalMessages;
        }

        /**
         * 트랜잭션 래퍼: 채팅방 배치 메시지 생성
         */
        @Transactional(propagation = Propagation.REQUIRES_NEW)
        public int createChatsForRoomBatchWithTransaction(List<ChatRoom> rooms, int minMessages, int maxMessages) {
            int totalMessages = 0;
            for (ChatRoom room : rooms) {
                totalMessages += createChatsForRoom(room, minMessages, maxMessages);
            }
            return totalMessages;
        }

        /**
         * 채팅방에 메시지 생성 (배치 처리 최적화)
         * 참여자들이 랜덤하게 메시지를 주고받는 패턴 시뮬레이션
         */
        private int createChatsForRoom(ChatRoom room, int minMessages, int maxMessages) {
            // 채팅방 참여자 조회
            List<ChatRoomParticipant> participants = participantRepository.findAllFetchMemberBy(room.getId());
            if (participants.isEmpty()) return 0;

            List<Member> roomMembers = participants.stream()
                    .map(ChatRoomParticipant::getMember)
                    .toList();

            // 메시지 수 결정
            int messageCount = minMessages + random.nextInt(maxMessages - minMessages + 1);
            List<Chat> chats = new ArrayList<>();

            // room은 다른 트랜잭션에서 생성된 detached 엔티티이므로 현재 트랜잭션의 프록시 참조 사용
            ChatRoom managedRoom = entityManager.getReference(ChatRoom.class, room.getId());

            // 메시지 생성
            for (int i = 0; i < messageCount; i++) {
                Member sender = roomMembers.get(random.nextInt(roomMembers.size()));
                String message = SAMPLE_MESSAGES[random.nextInt(SAMPLE_MESSAGES.length)];
                Chat chat = new Chat(message, sender, managedRoom);
                chats.add(chat);
            }

            // 메시지 일괄 저장
            List<Chat> savedChats = chatRepository.saveAll(chats);
            chatRepository.flush();

            // cursor 시뮬레이션: 각 멤버별로 읽은 위치를 랜덤하게 설정
            for (Member member : roomMembers) {
                int readCount = (int)(savedChats.size() * (0.5 + random.nextDouble() * 0.5));
                if (readCount > 0) {
                    Chat lastRead = savedChats.get(readCount - 1);
                    participantRepository.updateLastReadChatId(
                            member.getId(), managedRoom.getId(), lastRead.getId());
                }
            }
            entityManager.clear();

            return messageCount;
        }

        /**
         * 생성된 데이터 요약 출력
         */
        private void printSummary(List<Member> members, int directRoomCount, int groupRoomCount, int totalMessages) {
            log.info("");
            log.info("================== 생성 데이터 요약 ==================");
            log.info("사용자 수:           {}명", members.size());
            log.info("1:1 채팅방:          {}개 (각 30~80개 메시지)", directRoomCount);
            log.info("그룹 채팅방:         {}개 (각 50~100개 메시지)", groupRoomCount);
            log.info("총 채팅방:           {}개", directRoomCount + groupRoomCount);
            log.info("총 메시지:           약 {}개", totalMessages);

            int totalRecords = members.size() + directRoomCount + groupRoomCount
                             + (directRoomCount * 2) + (int)(groupRoomCount * 5.5)
                             + totalMessages;
            log.info("총 레코드 수:        약 {}개", totalRecords);
            log.info("====================================================");
            log.info("");
            log.info("============ 테스트 계정 샘플 (처음 5개) ============");
            log.info(String.format("| %-10s | %-10s | %-12s |", "Username", "Password", "Nickname"));
            log.info("|------------|------------|--------------|");
            for (Member member : members.subList(0, Math.min(5, members.size()))) {
                log.info(String.format("| %-10s | %-10s | %-12s |",
                    member.getUsername(),
                    member.getUsername(),
                    member.getNickname()));
            }
            if (members.size() > 5) {
                log.info("|    ...     |    ...     |     ...      |");
                Member last = members.get(members.size() - 1);
                log.info(String.format("| %-10s | %-10s | %-12s |",
                    last.getUsername(), last.getUsername(), last.getNickname()));
            }
            log.info("====================================================");
            log.info("💡 모든 사용자의 비밀번호는 username과 동일합니다");
            log.info("   예: user1 / user1, user100 / user100");
        }
    }
}
