package com.capsule.content.api;

import com.capsule.content.api.ContentTypes.BackgroundInfo;
import com.capsule.content.api.ContentTypes.RoomInfo;
import java.util.List;
import java.util.Optional;

public interface RoomQuery {

    /** 들어갈 수 있는 룸 (활성, 정렬 순서). 홈 화면용 */
    List<RoomInfo> activeRooms();

    /** 비활성 룸도 찾음 (지난 세션의 룸 이름 표시용) */
    Optional<RoomInfo> find(long roomId);

    /** 룸 기본 배경 (룸당 하나, DB가 보장) */
    Optional<BackgroundInfo> defaultBackground(long roomId);

    Optional<BackgroundInfo> background(long backgroundId);
}
