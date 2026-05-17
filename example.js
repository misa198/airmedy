export class KugouApi {
  static SEARCH_URL = "http://krcs.kugou.com/search";
  static DOWNLOAD_URL = "https://lyrics.kugou.com/download";

  constructor(title, artist, duration = null) {
    this.title = title;
    this.artist = artist;
    this.duration = duration;
  }

  async getKugouLrc() {
    for (let attempt = 0; attempt < 3; attempt++) {
      const keyword = this._buildKeyword();
      const duration = this.duration;
      const res = await this._searchLrc(keyword, duration);

      if (!res) return null;

      const candidates = res.candidates || [];
      if (candidates.length > 0) {
        const result = this._selectBestCandidate(candidates);
        if (result !== null) {
          const { id, accesskey } = result;
          return await this._downloadBestLrc(id, accesskey);
        }
      }

      if (attempt === 0) {
        console.log("未命中歌词，尝试清理括号内容...");
        this._cleanMetadata();
      } else if (attempt === 1) {
        console.log("去除括弧后依旧未命中,尝试互换key...");
        [this.artist, this.title] = [this.title, this.artist];
      } else {
        console.log(`最终未命中歌词!入参:${keyword},${duration}`);
      }
    }
    return null;
  }

  _buildKeyword() {
    return `${this.artist} - ${this.title}`;
  }

  async _searchLrc(keyword, duration) {
    const params = new URLSearchParams({
      ver: "1",
      man: "yes",
      client: "mobi",
      keyword,
      duration: duration ?? "",
      hash: "",
      album_audio_id: "",
    });

    try {
      const response = await fetch(`${KugouApi.SEARCH_URL}?${params}`);
      if (response.ok) return await response.json();
    } catch (e) {
      console.error(`请求出错: ${e.message}`);
    }
    return null;
  }

  _selectBestCandidate(candidates) {
    try {
      return candidates.reduce((prev, curr) => {
        const score = (c) => {
          const durationDiff = this.duration
            ? Math.abs(
                Math.floor(c.duration / 1000) -
                  Math.floor(this.duration / 1000),
              )
            : -c.score;
          return [durationDiff, -c.score];
        };

        const [pd0, pd1] = score(prev);
        const [cd0, cd1] = score(curr);
        if (cd0 !== pd0) return cd0 < pd0 ? curr : prev;
        return cd1 < pd1 ? curr : prev;
      });
    } catch (e) {
      console.error(`[候选选择错误] ${e.message}`);
      return null;
    }
  }

  async _downloadBestLrc(id, accesskey) {
    const params = new URLSearchParams({
      ver: "1",
      client: "pc",
      id,
      accesskey,
      fmt: "lrc",
      charset: "utf8",
    });

    try {
      const response = await fetch(`${KugouApi.DOWNLOAD_URL}?${params}`);
      const data = await response.json();
      return Buffer.from(data.content, "base64").toString("utf-8");
    } catch (e) {
      console.error(`解析歌词出错: ${e.message}`);
      return null;
    }
  }

  _cleanMetadata() {
    this.title = KugouApi._removeBrackets(this.title);
    this.artist = KugouApi._removeBrackets(this.artist);
  }

  static _removeBrackets(text) {
    return text.replace(
      /[\(\（\[\【\〔\{｛][^\)\）\]\】\〕\}｝]*[\)\）\]\】\〕\}｝]/g,
      "",
    );
  }
}
