# Redis 기반 화면 데이터 저장 예제

## 프로젝트 구조

```text
src/
├─ constants/
│  └─ saveTypes.js
│
├─ redis/
│  ├─ redisClient.js
│  ├─ redisKey.js
│  └─ redisStore.js
│
├─ services/
│  └─ screenSave.service.js
│
├─ controllers/
│  └─ screenSave.controller.js
│
├─ routes/
│  └─ screenSave.route.js
│
└─ server.js
```

## Redis Key 규칙

```text
저장타입:userId:화면id
```

예시:

```text
form:23:profile-edit
filter:23:order-list
draft:23:post-write
```

## `src/constants/saveTypes.js`

```javascript
const SAVE_TYPES = Object.freeze({
  FORM: "form",
  FILTER: "filter",
  DRAFT: "draft",
  SCROLL: "scroll",
  CACHE: "cache",
});

const SAVE_TYPE_VALUES = Object.values(SAVE_TYPES);

const isValidSaveType = (saveType) => {
  return SAVE_TYPE_VALUES.includes(saveType);
};

module.exports = {
  SAVE_TYPES,
  SAVE_TYPE_VALUES,
  isValidSaveType,
};
```

## `src/redis/redisClient.js`

```javascript
const { createClient } = require("redis");

const redisClient = createClient({
  url: process.env.REDIS_URL || "redis://localhost:6379",
});

redisClient.on("error", (error) => {
  console.error("Redis Client Error:", error);
});

const connectRedis = async () => {
  if (!redisClient.isOpen) {
    await redisClient.connect();
  }
};

module.exports = {
  redisClient,
  connectRedis,
};
```

## `src/redis/redisKey.js`

```javascript
const { isValidSaveType } = require("../constants/saveTypes");

const encodeKeyPart = (value) => {
  return encodeURIComponent(String(value));
};

const buildScreenSaveKey = ({ saveType, userId, screenId }) => {
  if (!saveType || !userId || !screenId) {
    throw new Error("saveType, userId, screenId는 필수입니다.");
  }

  if (!isValidSaveType(saveType)) {
    throw new Error(`허용되지 않은 saveType입니다: ${saveType}`);
  }

  return [
    encodeKeyPart(saveType),
    encodeKeyPart(userId),
    encodeKeyPart(screenId),
  ].join(":");
};

module.exports = {
  buildScreenSaveKey,
};
```

## `src/redis/redisStore.js`

```javascript
const { redisClient } = require("./redisClient");
const { buildScreenSaveKey } = require("./redisKey");

const redisStore = {
  async set({ saveType, userId, screenId, value, ttlSeconds }) {
    const key = buildScreenSaveKey({
      saveType,
      userId,
      screenId,
    });

    const stringValue = JSON.stringify(value);

    if (ttlSeconds) {
      await redisClient.set(key, stringValue, {
        EX: ttlSeconds,
      });
    } else {
      await redisClient.set(key, stringValue);
    }

    return key;
  },

  async get({ saveType, userId, screenId }) {
    const key = buildScreenSaveKey({
      saveType,
      userId,
      screenId,
    });

    const value = await redisClient.get(key);

    if (!value) {
      return null;
    }

    return JSON.parse(value);
  },

  async remove({ saveType, userId, screenId }) {
    const key = buildScreenSaveKey({
      saveType,
      userId,
      screenId,
    });

    return redisClient.del(key);
  },

  async exists({ saveType, userId, screenId }) {
    const key = buildScreenSaveKey({
      saveType,
      userId,
      screenId,
    });

    const result = await redisClient.exists(key);

    return result === 1;
  },

  async expire({ saveType, userId, screenId, ttlSeconds }) {
    const key = buildScreenSaveKey({
      saveType,
      userId,
      screenId,
    });

    return redisClient.expire(key, ttlSeconds);
  },
};

module.exports = {
  redisStore,
};
```

## `src/services/screenSave.service.js`

```javascript
const { redisStore } = require("../redis/redisStore");

const DEFAULT_TTL_SECONDS = 60 * 60 * 24; // 1일

const screenSaveService = {
  async save({ saveType, userId, screenId, value, ttlSeconds }) {
    return redisStore.set({
      saveType,
      userId,
      screenId,
      value,
      ttlSeconds: ttlSeconds || DEFAULT_TTL_SECONDS,
    });
  },

  async find({ saveType, userId, screenId }) {
    return redisStore.get({
      saveType,
      userId,
      screenId,
    });
  },

  async remove({ saveType, userId, screenId }) {
    return redisStore.remove({
      saveType,
      userId,
      screenId,
    });
  },

  async exists({ saveType, userId, screenId }) {
    return redisStore.exists({
      saveType,
      userId,
      screenId,
    });
  },
};

module.exports = {
  screenSaveService,
};
```

## `src/controllers/screenSave.controller.js`

```javascript
const { screenSaveService } = require("../services/screenSave.service");

const saveScreenData = async (req, res, next) => {
  try {
    const { saveType, screenId, value } = req.body;

    // 로그인 미들웨어에서 넣어준 userId라고 가정
    const userId = req.user.id;

    const key = await screenSaveService.save({
      saveType,
      userId,
      screenId,
      value,
    });

    return res.status(200).json({
      message: "저장되었습니다.",
      key,
    });
  } catch (error) {
    next(error);
  }
};

const getScreenData = async (req, res, next) => {
  try {
    const { saveType, screenId } = req.query;

    // 로그인 미들웨어에서 넣어준 userId라고 가정
    const userId = req.user.id;

    const data = await screenSaveService.find({
      saveType,
      userId,
      screenId,
    });

    return res.status(200).json({
      data,
    });
  } catch (error) {
    next(error);
  }
};

const deleteScreenData = async (req, res, next) => {
  try {
    const { saveType, screenId } = req.body;

    // 로그인 미들웨어에서 넣어준 userId라고 가정
    const userId = req.user.id;

    await screenSaveService.remove({
      saveType,
      userId,
      screenId,
    });

    return res.status(200).json({
      message: "삭제되었습니다.",
    });
  } catch (error) {
    next(error);
  }
};

module.exports = {
  saveScreenData,
  getScreenData,
  deleteScreenData,
};
```

## `src/routes/screenSave.route.js`

```javascript
const express = require("express");

const {
  saveScreenData,
  getScreenData,
  deleteScreenData,
} = require("../controllers/screenSave.controller");

const router = express.Router();

router.post("/screen-save", saveScreenData);
router.get("/screen-save", getScreenData);
router.delete("/screen-save", deleteScreenData);

module.exports = router;
```

## `src/server.js`

```javascript
const express = require("express");
const { connectRedis } = require("./redis/redisClient");
const screenSaveRouter = require("./routes/screenSave.route");

const app = express();

app.use(express.json());

// 예시용 로그인 사용자 mock
// 실제 프로젝트에서는 JWT 인증 미들웨어 등에서 req.user를 넣어주면 됨
app.use((req, res, next) => {
  req.user = {
    id: 23,
  };

  next();
});

app.use("/api", screenSaveRouter);

const bootstrap = async () => {
  await connectRedis();

  app.listen(3000, () => {
    console.log("Server running on port 3000");
  });
};

bootstrap();
```

## 사용 예시

### 저장 요청

```http
POST /api/screen-save
Content-Type: application/json

{
  "saveType": "form",
  "screenId": "profile-edit",
  "value": {
    "name": "홍길동",
    "email": "test@test.com"
  }
}
```

Redis key 결과:

```text
form:23:profile-edit
```

### 조회 요청

```http
GET /api/screen-save?saveType=form&screenId=profile-edit
```

응답 예시:

```json
{
  "data": {
    "name": "홍길동",
    "email": "test@test.com"
  }
}
```

### 삭제 요청

```http
DELETE /api/screen-save
Content-Type: application/json

{
  "saveType": "form",
  "screenId": "profile-edit"
}
```

## 내부 코드에서 직접 사용할 때

```javascript
const { SAVE_TYPES } = require("./constants/saveTypes");
const { screenSaveService } = require("./services/screenSave.service");

await screenSaveService.save({
  saveType: SAVE_TYPES.FORM,
  userId: 23,
  screenId: "profile-edit",
  value: {
    name: "홍길동",
    email: "test@test.com",
  },
});

const data = await screenSaveService.find({
  saveType: SAVE_TYPES.FORM,
  userId: 23,
  screenId: "profile-edit",
});

await screenSaveService.remove({
  saveType: SAVE_TYPES.FORM,
  userId: 23,
  screenId: "profile-edit",
});
```
