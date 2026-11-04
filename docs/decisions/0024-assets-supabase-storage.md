# 0024 Live2D · 이미지 에셋은 Supabase Storage 공개 버킷

- 결정
  - 에셋 파일(Live2D `.moc3` · `.model3.json` · 텍스처 · `.exp3.json`, 이후 이미지)은 Supabase Storage의 공개 버킷 `live2d-model`에 둔다. 레포에는 넣지 않는다
  - 서버는 파일 경로만 DB에 저장하고(`live2d_models.storage_prefix` · `model_json_path`, `live2d_expressions.file_path`), 응답에서 `CAPSULE_CDN_BASE_URL` 뒤에 붙인다. 값: `https://<project-ref>.supabase.co/storage/v1/object/public/live2d-model`
  - 경로는 `<모델>/v<버전>/`(예: `chaerin/v1/`). 에셋을 바꿀 때는 덮어쓰지 않고 새 버전 폴더로 올린다(브라우저 · CDN 캐시)
  - 파일 이름은 영문 · 숫자만. Supabase Storage가 한글 · 공백이 든 키를 거부해서, 디자이너 원본(`채린 라투디 데모용.*`)은 `chaerin.*`로 바꾸고 `model3.json`의 경로도 맞췄다
- 이유
  - DB를 이미 Supabase로 쓰고 있어(ADR 0017) 계정 · 대시보드를 새로 만들 필요가 없다. 공개 버킷은 CORS `*`로 열려 있어 프론트가 바로 불러온다(2026-11-05 확인)
  - Live2D 파일은 크고 바이너리라 git에 넣으면 레포가 무거워진다
- 버린 대안
  - 레포(백엔드 `static/`)에 넣기: 레포가 무거워지고 에셋을 바꿀 때마다 배포가 필요하다
  - 프론트 레포 `public/`: 가장 빠르지만 에셋 교체가 프론트 배포에 묶인다
  - S3 · Cloudflare R2: 새 계정 · 결제 설정이 필요하다. 트래픽이 커지면 다시 본다
- 대가
  - 무료 플랜의 저장 용량 · 전송량 한도에 묶인다. 시연 규모에서는 충분하다
  - 공개 버킷이라 주소를 아는 사람은 누구나 받는다(에셋은 공개돼도 되는 것만 둔다)
