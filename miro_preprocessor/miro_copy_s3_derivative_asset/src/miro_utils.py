import json
import re


class MiroImage:
    def __init__(self, image_info_json):
        image_info = json.loads(image_info_json)
        self.collection = image_info['collection']
        self.image_data = image_info['image_data']

    @property
    def miro_id(self):
        return self.image_data['image_no_calc']

    @property
    def shard(self):
        result = re.match(r"(?P<shard>[A-Z]+[0-9]{4})", self.miro_id)
        return f"{result.group('shard')}000"

    @property
    def image_path(self):
        return f"{self.shard}/{self.miro_id}.jpg"
