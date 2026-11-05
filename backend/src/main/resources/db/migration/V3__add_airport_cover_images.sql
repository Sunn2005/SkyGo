-- Destination photos served from backend/src/main/resources/static/airports.
-- Existing airports outside the demo catalog keep a NULL image path.
ALTER TABLE airport ADD COLUMN cover_image_path VARCHAR(255) NULL;

UPDATE airport
   SET cover_image_path = CONCAT('/airports/', code, '.jpg')
 WHERE code IN (
   'HAN', 'SGN', 'DAD', 'PQC', 'HND', 'GMP', 'SIN', 'BKK',
   'DEL', 'KTM', 'LHR', 'CDG', 'CBR', 'WLG', 'IAD', 'YOW',
   'AUH', 'DOH', 'NBO', 'ABV', 'NQZ', 'TAS', 'ESB', 'GYD'
 );
